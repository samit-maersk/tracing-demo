package com.samitkumarpatel.tracingdemo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

@SpringBootApplication
@Slf4j
public class TracingDemoApplication {

	public static void main(String[] args) {
		Hooks.enableAutomaticContextPropagation();
		SpringApplication.run(TracingDemoApplication.class, args);
	}


	@Bean
	public RouterFunction routerFunction(UserServices userServices) {
		return RouterFunctions
				.route()
				.GET("/person", request -> ServerResponse.ok().body(userServices.allUsers(), User.class))
				.GET("/person/{id}", request -> {
					var id = Integer.parseInt(request.pathVariable("id"));
					return ServerResponse.ok().body(userServices.userById(id), User.class);
				})
				.after((request, response) -> {
					log.info("{} {}",request.path(), response.statusCode());
					return response;
				})
				.build();
	}
}

record User(int id, String name, String email, Address address, String phone, String website, Company company, @JsonProperty("employment") EmploymentDetails employmentDetails) {}
record Address(String street, String suite, String city, String zipcode, Geo geo) {}
record Company(String name, String catchPhrase, String bs) {}
record Geo(String lat, String lng) {}
record EmploymentDetails(@Id int id, String designation, int salary) {}

@Repository
interface EmploymentDetailsRepository extends R2dbcRepository <EmploymentDetails, Integer> {}

@Slf4j
@Service
@RequiredArgsConstructor
class UserServices {
	private final UserClient userClient;
	private final EmploymentDetailsRepository employmentDetailsRepository;

	public Flux<User> allUsers() {
		return Flux.zip(
				userClient.allUsers()
						.doOnComplete(() -> log.info("service call success"))
						.doOnError(e -> log.error("service call error")),
				employmentDetailsRepository.findAll()
						.doOnComplete(() -> log.info("db call success"))
						.doOnError(e -> log.error("db call error")),
				(user, employmentDetails) -> new User(
						user.id(),
						user.name(),
						user.email(),
						user.address(),
						user.phone(),
						user.website(),
						user.company(),
						employmentDetails
				)
		);
	}

	public Mono<User> userById(int id) {
		return Mono.zip(
				userClient.userById(id).doOnSuccess(s -> log.info("service call success")).doOnError(e -> log.error("service call error")),
				employmentDetailsRepository.findById(id).doOnSuccess(s -> log.info("db call success")).doOnError(e -> log.error("db call error")),
				(user, employmentDetails) -> new User(
						user.id(),
						user.name(),
						user.email(),
						user.address(),
						user.phone(),
						user.website(),
						user.company(),
						employmentDetails
				)
		);
	}

}

@HttpExchange(url = "/users")
interface UserClient {
	@GetExchange
	Flux<User> allUsers();
	@GetExchange(url = "/{id}")
	Mono<User> userById(@PathVariable("id") int id);
}


@Configuration
@Slf4j
class Configurations {
	@Bean
	UserClient userClient(WebClient.Builder builder, @Value("${spring.application.jsonplaceHolder.host}") String baseUrl) {
		builder.defaultHeader("Accept", "application/json");
		var webClientAdapter = WebClientAdapter.forClient(builder.baseUrl(baseUrl).build());
		var httpServiceProxyFactory = HttpServiceProxyFactory.builder(webClientAdapter).build();
		return httpServiceProxyFactory.createClient(UserClient.class);
	}
}