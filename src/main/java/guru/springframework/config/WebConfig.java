package guru.springframework.config;

import guru.springframework.domain.Recipe;
import guru.springframework.services.RecipeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class WebConfig {

    @Bean
    RouterFunction<?> routes(RecipeService recipeService) {
        return RouterFunctions.route(RequestPredicates.GET("/api/recipes"),
                serviceRequest -> ServerResponse
                        .ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(recipeService.getRecipes(), Recipe.class));
    }
}
