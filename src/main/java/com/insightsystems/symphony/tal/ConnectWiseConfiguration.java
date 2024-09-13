package com.insightsystems.symphony.tal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.avispl.symphony.api.tal.TalAdapter;
import com.avispl.symphony.api.tal.TalConfigService;
import com.avispl.symphony.api.tal.TalProxy;

@Configuration
public class ConnectWiseConfiguration {

	/**
	 * CW now adapter tal adapter will be scanned by TAL microservice and used for synchronization of tickets
	 * TAL context will provide configuration and routing services
	 *
	 * @param talConfigService the tal config service
	 * @param talRoutingService the tal routing service
	 * @return the tal adapter
	 */
	@Bean(name = "talCWAdapter")
	@ConditionalOnMissingBean
	public TalAdapter talCWAdapter(
			TalConfigService talConfigService,
			TalProxy talRoutingService) {
		return new ConnectWiseTalAdapter(talConfigService, talRoutingService);
	}
}
