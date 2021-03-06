package at.knowcenter.recommender.solrpowered.evaluation.concurent.jobs.impl.social;

import java.util.List;
import java.util.concurrent.Callable;

import at.knowcenter.recommender.solrpowered.engine.filtering.ContentFilter;
import at.knowcenter.recommender.solrpowered.engine.strategy.StrategyType;
import at.knowcenter.recommender.solrpowered.evaluation.concurent.jobs.AbstractStrategyJob;
import at.knowcenter.recommender.solrpowered.evaluation.metrics.MetricsExporter;

public class UB_InterestsWithOutMLT_Job extends AbstractStrategyJob implements Callable<List<MetricsExporter>>{

	private List<String> users;


	public UB_InterestsWithOutMLT_Job(List<String> users, String jobDescription) {
		this.users = users;
		this.jobDescription = jobDescription;
	}

	@Override
	public List<MetricsExporter> call() throws Exception {
		ContentFilter cf = new ContentFilter();
		List<MetricsExporter> metricsCalcs = initMetricCalcs("13_" + StrategyType.UB_WithOutMLT.name());
		
		System.out.println("Evaluation over " + users.size() + " users");
		
		evaluate(cf, metricsCalcs, StrategyType.UB_WithOutMLT);
		
		return metricsCalcs;
	}

	@Override
	protected List<String> getUsers() {
		return users;
	}
	
}
