package at.knowcenter.recommender.solrpowered.services;

import at.knowcenter.recommender.solrpowered.configuration.ConfigUtils;
import at.knowcenter.recommender.solrpowered.services.impl.actions.RecommendService;
import at.knowcenter.recommender.solrpowered.services.impl.item.ItemService;
import at.knowcenter.recommender.solrpowered.services.impl.social.SocialActionService;
import at.knowcenter.recommender.solrpowered.services.impl.social.reversed.OwnSocialActionService;
import at.knowcenter.recommender.solrpowered.services.impl.socialstream.SocialStreamService;
import at.knowcenter.recommender.solrpowered.services.impl.user.UserService;

/**
 * Contains all services which access SOLR.
 * <br/>
 * Used as a container to get needed services without the need to manually configure them
 * @author elacic
 *
 */
public class SolrServiceContainer {
	
	private RecommendService recommendService;
	private UserService userService;
	private ItemService itemService;
	private SocialActionService socialActionService;
	private OwnSocialActionService ownSocialActionService;
	private SocialStreamService socialStreamService;
	
	/**
	 * Initialization-on-demand holder idiom
	 * <br/>
	 * {@link http://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom}
	 * @author elacic
	 *
	 */
	private static class Holder {
        static final SolrServiceContainer INSTANCE = new SolrServiceContainer();
    }
	
	public static SolrServiceContainer getInstance() {
        return Holder.INSTANCE;
    }
	
	private SolrServiceContainer() {
	    ConfigUtils.loadConfiguration();
	    boolean success = init();
	    if (! success){
	    	throw new RuntimeException("All Solr services, as defined in the configuration, could not be initialized!");
	    }
	}
	
	
	private boolean init() {
		boolean initSuccess = false;
		
		int port = 8984;
		String address = "localhost";
		
		try {
			UserService userService = new UserService(address, port, "collection3");
			RecommendService recommenderService = new RecommendService(address, port, "collection2");
			ItemService itemService = new ItemService(address, port, "collection1");
			SocialActionService socialService = new SocialActionService(address, port, "bn_social_action");
			OwnSocialActionService ownSocialService = new OwnSocialActionService(address, port, "bn_own_social_action");
			SocialStreamService socialStreamService = new SocialStreamService(address, port, "bn_social_stream");
			
			setUserService(userService);
			setRecommendService(recommenderService);
			setItemService(itemService);
			setSocialActionService(socialService);
			setOwnSocialActionService(ownSocialService);
			setSocialStreamService(socialStreamService);
		
			initSuccess = true;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		return initSuccess;
	}

	public RecommendService getRecommendService() {
		return recommendService;
	}

	public void setRecommendService(RecommendService recommendService) {
		this.recommendService = recommendService;
	}

	public UserService getUserService() {
		return userService;
	}

	public ItemService getItemService() {
		return itemService;
	}

	public void setItemService(ItemService searchService) {
		this.itemService = searchService;
	}

	public void setUserService(UserService userService) {
		this.userService = userService;
	}
	
	public SocialActionService getSocialActionService() {
		return socialActionService;
	}

	public void setSocialActionService(SocialActionService socialActionService) {
		this.socialActionService = socialActionService;
	}

	public OwnSocialActionService getOwnSocialActionService() {
		return ownSocialActionService;
	}

	public void setOwnSocialActionService(OwnSocialActionService ownSocialActionService) {
		this.ownSocialActionService = ownSocialActionService;
	}

	public SocialStreamService getSocialStreamService() {
		return socialStreamService;
	}

	public void setSocialStreamService(SocialStreamService socialStreamService) {
		this.socialStreamService = socialStreamService;
	}
	
	
	

}
