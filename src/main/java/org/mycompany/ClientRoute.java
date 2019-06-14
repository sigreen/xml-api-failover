package org.mycompany;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientRoute extends RouteBuilder {
	
	@Value("${hystrix.executionTimeout}")
	private int hystrixExecutionTimeout;
	
	@Value("${hystrix.groupKey}")
	private String hystrixGroupKey;
	
	@Value("${hystrix.circuitBreakerEnabled}")
	private boolean hystrixCircuitBreakerEnabled;

    @Override
    public void configure() {
        from("jetty:http://0.0.0.0:{{httpGateway.port}}/countryHolidayLookup").routeId("country-holiday-lookup").streamCaching()
	    	.removeHeaders("*", "country", "year")
	    	.log("Country: [${header.country}] Year: [${header.year}]")
	    	.hystrix().id("GetCountriesAvailable")
                .to("http4://www.holidaywebservice.com/HolidayService_v2/HolidayService2.asmx/GetCountriesAvailable")
                .onFallbackViaNetwork()
                	.transform().simple("Validation service unavailable")
                .end() 
                .choice()
                	.when().xpath("//*[local-name() = 'Code'] = in:header('country')")
                		.to("direct:processHolidays")
                	.otherwise().transform().simple("${header.country} is not a supported country")
                .end()
        ;
        
        from("direct:processHolidays").routeId("process-holidays").streamCaching()
			.log("We are valid")
			.removeHeaders("*", "country", "year")
	    	.hystrix().id("GetHolidaysForYear")
	    		.toD("http4://www.holidaywebservice.com/HolidayService_v2/HolidayService2.asmx/GetHolidaysForYear?countryCode=${header.country}&year=${header.year}")
	        	.onFallbackViaNetwork()
	    			.transform().simple("Error occured calling GetHolidaysForYear API")
	    		.end()
	    ;
    }

}