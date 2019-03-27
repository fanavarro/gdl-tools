package se.cambio.cds.model.facade.ehr.service.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import se.cambio.cds.model.facade.ehr.delegate.EhrService;
import se.cambio.cds.model.facade.ehr.service.EhrServiceImpl;

@Configuration
@Profile("ehr-service")
public class EhrServiceConfiguration {
	@Bean
    EhrService ehrService() {
        return new EhrServiceImpl();
    }
}
