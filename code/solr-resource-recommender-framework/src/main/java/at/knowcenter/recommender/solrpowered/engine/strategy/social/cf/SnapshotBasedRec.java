package at.knowcenter.recommender.solrpowered.engine.strategy.social.cf;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.ModifiableSolrParams;

import at.knowcenter.recommender.solrpowered.engine.filtering.ContentFilter;
import at.knowcenter.recommender.solrpowered.engine.strategy.RecommendStrategy;
import at.knowcenter.recommender.solrpowered.engine.strategy.StrategyType;
import at.knowcenter.recommender.solrpowered.engine.strategy.marketplace.cf.ReviewBasedRec;
import at.knowcenter.recommender.solrpowered.engine.utils.CFQueryBuilder;
import at.knowcenter.recommender.solrpowered.engine.utils.RecommendationQueryUtils;
import at.knowcenter.recommender.solrpowered.model.CustomerAction;
import at.knowcenter.recommender.solrpowered.model.Resource;
import at.knowcenter.recommender.solrpowered.model.SocialAction;
import at.knowcenter.recommender.solrpowered.services.SolrServiceContainer;
import at.knowcenter.recommender.solrpowered.services.impl.actions.RecommendQuery;
import at.knowcenter.recommender.solrpowered.services.impl.actions.RecommendResponse;
import at.knowcenter.recommender.solrpowered.services.impl.item.ItemQuery;

/**
 * Collaborative Filtering Recommender strategy
 * @author elacic
 *
 */
public class SnapshotBasedRec implements RecommendStrategy {

	public static int MAX_USER_OCCURENCE_COUNT = CFQueryBuilder.MAX_USER_OCCURENCE_COUNT;
	private List<String> alreadyBoughtProducts;
	private ContentFilter contentFilter;

	@Override
	public RecommendResponse recommend(RecommendQuery query, Integer maxReuslts){
		ModifiableSolrParams solrParams = new ModifiableSolrParams();
		QueryResponse response = null;
		RecommendResponse searchResponse = new RecommendResponse();
		
		long step0ElapsedTime = 0;
		long step1ElapsedTime;
		List<String> recommendations = new ArrayList<String>();

		try {
			// STEP 0 - get products from a user
			if (query.getUser() != null ) {
				if (query.getProductIds() == null || query.getProductIds().size() == 0) {
					if (alreadyBoughtProducts != null) {
						query.setProductIds(alreadyBoughtProducts);
					} else {
					}
				}
			}
			
			solrParams = getInteractionsFromMeAndUSersThatILikedOrCommented(query.getUser());

			response = SolrServiceContainer.getInstance().getSocialActionService().getSolrServer().query(solrParams);
			step1ElapsedTime = response.getElapsedTime();
			
			
			List<SocialAction> socialUsers = response.getBeans(SocialAction.class);
			
			if (socialUsers.size() == 0) {
				searchResponse.setNumFound(0);
				searchResponse.setResultItems(recommendations);
				searchResponse.setElapsedTime(-1);
				return searchResponse;
			}
			
			SocialAction currentUserInteractions = socialUsers.get(0);
			if (currentUserInteractions.getUserId().equals(query.getUser())){
				socialUsers.remove(0);
			} else {
				currentUserInteractions = null;
			}
			
			final Map<String, Integer> userInteractionMap = new HashMap<String, Integer>();


			if (currentUserInteractions != null) {
				List<String> usersThatPostedASnapshopToMe = currentUserInteractions.getUsersThatPostedASnapshopToMe();
				
				if (usersThatPostedASnapshopToMe != null) {
					fillInteractions(usersThatPostedASnapshopToMe, userInteractionMap);
				}
			}
			
			for (SocialAction socialUser : socialUsers) {
				Integer userInteraction = userInteractionMap.get(socialUser.getUserId());
				if (userInteraction == null) {
					userInteraction = 0;
				}
				
				if (socialUser.getUsersThatPostedASnapshopToMe() != null) {
					userInteraction += Collections.frequency(socialUser.getUsersThatPostedASnapshopToMe(), query.getUser());
				}
				
				userInteractionMap.put(socialUser.getUserId(), userInteraction);
			}
			
			
			Comparator<String> interactionCountComparator = new Comparator<String>() {

				
				@Override
				public int compare(String a, String b) {
					if (userInteractionMap.get(a) > userInteractionMap.get(b)) {
			            return -1;
			        } else if (userInteractionMap.get(a).equals(userInteractionMap.get(b))){
			        	return 0;
			        } else {
			            return 1;
			        }
				}
				
			};
			
	        TreeMap<String,Integer> sorted_map = new TreeMap<String,Integer>(interactionCountComparator);
	        sorted_map.putAll(userInteractionMap);
	        solrParams = getSTEP2Params(query, maxReuslts, sorted_map);
			// TODO Facet for confidence value
			response = SolrServiceContainer.getInstance().getResourceService().getSolrServer().query(solrParams);
			// fill response object
			List<Resource> beans = response.getBeans(Resource.class);
			searchResponse.setResultItems(RecommendationQueryUtils.extractRecommendationIds(beans));
			searchResponse.setElapsedTime(step0ElapsedTime + step1ElapsedTime + response.getElapsedTime());

			SolrDocumentList docResults = response.getResults();
			searchResponse.setNumFound(docResults.getNumFound());
		} catch (Exception e) {
			System.out.println(solrParams);
			e.printStackTrace();
			searchResponse.setNumFound(0);
			searchResponse.setResultItems(recommendations);
			searchResponse.setElapsedTime(-1);
		}
		
		return searchResponse;
	}


	private void fillInteractions(List<String> users, Map<String, Integer> userInteractionMap) {
		for(String interactedUser : users) {

			Integer userInteraction = userInteractionMap.get(interactedUser);

			if (userInteraction == null) {
				userInteraction = 0;
			}

			userInteractionMap.put(interactedUser, userInteraction + 1);
		}
	}


	private ModifiableSolrParams getSTEP2Params(
			RecommendQuery query, Integer maxReuslts, Map<String, Integer> userInteractionMap) {
		ModifiableSolrParams solrParams = new ModifiableSolrParams();
		
		String queryString = createQueryToFindProdLikedBySimilarSocialUsers(userInteractionMap, contentFilter, MAX_USER_OCCURENCE_COUNT);
		
		String filterQueryString = 
				RecommendationQueryUtils.buildFilterForContentBasedFiltering(contentFilter);
		
		if (alreadyBoughtProducts != null && alreadyBoughtProducts.size() > 0) {
			if (filterQueryString.trim().length() > 0) {
				filterQueryString += " OR ";
			}
			filterQueryString += RecommendationQueryUtils.buildFilterForAlreadyBoughtProducts("id",alreadyBoughtProducts);
		}
		
		solrParams.set("q", queryString);
		solrParams.set("fq", filterQueryString);
		solrParams.set("fl", "id");
		solrParams.set("rows", maxReuslts);
		return solrParams;
	}
	
	public static String createQueryToFindProdLikedBySimilarSocialUsers(
			Map<String, Integer> userInteractionMap, ContentFilter contentFilter, int maxUserOccurence) {
		String query = createQueryToFindProdLikedBySimilarUsers(
				userInteractionMap, contentFilter, ReviewBasedRec.USERS_RATED_5_FIELD, maxUserOccurence, 1.0);
		query += " OR " + createQueryToFindProdLikedBySimilarUsers(
				userInteractionMap, contentFilter, ReviewBasedRec.USERS_RATED_4_FIELD, maxUserOccurence, 2.0);
		query += " OR " + createQueryToFindProdLikedBySimilarUsers(
				userInteractionMap, contentFilter, ReviewBasedRec.USERS_RATED_3_FIELD, maxUserOccurence, 3.0);
		query += " OR " + createQueryToFindProdLikedBySimilarUsers(
				userInteractionMap, contentFilter, ReviewBasedRec.USERS_RATED_2_FIELD, maxUserOccurence, 4.0);
		query += " OR " + createQueryToFindProdLikedBySimilarUsers(
				userInteractionMap, contentFilter, ReviewBasedRec.USERS_RATED_1_FIELD, maxUserOccurence, 5.0);
		
		return query;
	}
	
	public static String createQueryToFindProdLikedBySimilarUsers(
			Map<String, Integer> userInteractionMap, 
			ContentFilter contentFilter,
			String usersFieldName,
			int maxUserOccurence,
			double weightDividor) {
		StringBuilder queryBuilder = new StringBuilder();

		queryBuilder.append(usersFieldName + ":(");
		
		if (weightDividor <= 0.0) {
			queryBuilder.append("\"\")");
			return queryBuilder.toString();
		}
		//  max users
		int userOccurenceCount = 0;
		
		for (String user : userInteractionMap.keySet()) {
			if (userOccurenceCount >= maxUserOccurence) { break; }
			Double boosting = userInteractionMap.get(user) / weightDividor ;
			boosting = ((int) (boosting * 100)) / 100.0;
			
			queryBuilder.append(user + "^" + boosting + " OR ");
			userOccurenceCount++;
		}
		
		if (queryBuilder.length() > (usersFieldName + ":(").length()){
			queryBuilder.replace(queryBuilder.length() - 3, queryBuilder.length(), "");
		} else {
			queryBuilder.append("\"\"");
		}
		
		queryBuilder.append(")");
		return queryBuilder.toString();
	}

	private ModifiableSolrParams getInteractionsFromMeAndUSersThatILikedOrCommented(String user) {
		ModifiableSolrParams solrParams = new ModifiableSolrParams();
		String queryString = "id:(\"" + user + "\"^2) OR users_that_posted_a_snapshot_to_me:(\"" + user + "\")";
		
		solrParams.set("q", queryString);
		solrParams.set("rows", Integer.MAX_VALUE);
		return solrParams;
	}
	
	

	@Override
	public void setAlreadyPurchasedResources(List<String> alreadyBoughtProducts) {
		this.alreadyBoughtProducts = alreadyBoughtProducts;
	}

	@Override
	public List<String> getAlreadyBoughtProducts() {
		return alreadyBoughtProducts;
	}
	
	@Override
	public void setContentFiltering(ContentFilter contentFilter) {
		this.contentFilter = contentFilter;
	}
	
	@Override
	public StrategyType getStrategyType() {
		return StrategyType.SnapshotInteraction;
	}

}
