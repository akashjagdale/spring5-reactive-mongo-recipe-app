package guru.springframework.services;

import guru.springframework.commands.IngredientCommand;
import guru.springframework.converters.IngredientCommandToIngredient;
import guru.springframework.converters.IngredientToIngredientCommand;
import guru.springframework.domain.Ingredient;
import guru.springframework.domain.Recipe;
import guru.springframework.domain.UnitOfMeasure;
import guru.springframework.repositories.reactive.RecipeReactiveRepository;
import guru.springframework.repositories.reactive.UnitOfMeasureReactiveRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Created by jt on 6/28/17.
 */
@Slf4j
@Service
public class IngredientServiceImpl implements IngredientService {

    private final IngredientToIngredientCommand ingredientToIngredientCommand;
    private final IngredientCommandToIngredient ingredientCommandToIngredient;
    private final RecipeReactiveRepository recipeReactiveRepository;
    private final UnitOfMeasureReactiveRepository unitOfMeasureReactiveRepository;

    public IngredientServiceImpl(IngredientToIngredientCommand ingredientToIngredientCommand,
                                 IngredientCommandToIngredient ingredientCommandToIngredient,
                                 RecipeReactiveRepository recipeReactiveRepository,
                                 UnitOfMeasureReactiveRepository unitOfMeasureReactiveRepository) {
        this.ingredientToIngredientCommand = ingredientToIngredientCommand;
        this.ingredientCommandToIngredient = ingredientCommandToIngredient;
        this.recipeReactiveRepository = recipeReactiveRepository;
        this.unitOfMeasureReactiveRepository = unitOfMeasureReactiveRepository;
    }

    @Override
    public Mono<IngredientCommand> findByRecipeIdAndIngredientId(String recipeId, String ingredientId) {
        return recipeReactiveRepository
                .findById(recipeId)
                .flatMapIterable(Recipe::getIngredients)
                .filter(ingredient -> ingredient.getId().equalsIgnoreCase(ingredientId))
                .single()
                .map(ingredient -> {
                    IngredientCommand command = ingredientToIngredientCommand.convert(ingredient);
                    command.setRecipeId(recipeId);
                    return command;
                });
    }

    @Override
    public Mono<IngredientCommand> saveIngredientCommand(IngredientCommand command) {

        return recipeReactiveRepository.findById(command.getRecipeId())
                .map(recipe -> {
                    if (recipe == null) {
                        log.error("Recipe not found for id: " + command.getRecipeId());
                        return new IngredientCommand();
                    } else {
                        Flux<UnitOfMeasure> uomFlux = unitOfMeasureReactiveRepository.findAll();

                        log.error("Recipe not found");
                        Optional<Ingredient> ingredientOptional = recipe.getIngredients().stream()
                                .filter(ingredient -> ingredient.getId().equals(command.getId()))
                                .findFirst();
                        if (ingredientOptional.isPresent()) {
                            log.error("Ingredient found");
                            Ingredient ingredientFound = ingredientOptional.get();
                            ingredientFound.setDescription(command.getDescription());
                            ingredientFound.setAmount(command.getAmount());
                            UnitOfMeasure uom = uomFlux.filter(unitOfMeasure -> unitOfMeasure.getId().equals(command.getUom().getId())).single().toProcessor().block();
                            unitOfMeasureReactiveRepository
                                    .findById(command.getUom().getId())
                                    .subscribe(unitOfMeasure -> ingredientFound.setUom(unitOfMeasure));
//                                    .orElseThrow(() -> new RuntimeException("UOM NOT FOUND"))); //todo address this
                        } else {
                            log.error("Ingredient created");
                            //add new Ingredient
                            Ingredient ingredientCreated = new Ingredient();
                            ingredientCreated.setDescription(command.getDescription());
                            ingredientCreated.setAmount(command.getAmount());

                            unitOfMeasureReactiveRepository
                                    .findById(command.getUom().getId())
                                    .subscribe(unitOfMeasure -> {
                                        ingredientCreated.setUom(unitOfMeasure);
                                    });
                            recipe.addIngredient(ingredientCreated);
                        }

                        log.error("Recipe saving");
                        return recipeReactiveRepository.save(recipe)
                                .doOnError(throwable -> new RuntimeException("Error while saving ingredient"))
                                .map(savedRecipe -> {
                                    Optional<Ingredient> savedIngredientOptional = savedRecipe.getIngredients().stream()
                                            .filter(recipeIngredients -> recipeIngredients.getId().equals(command.getId()))
                                            .findFirst();
                                    log.error("Got Recipe saved");
                                    //check by description
                                    if (!savedIngredientOptional.isPresent()) {
                                        //not totally safe... But best guess
                                        savedIngredientOptional = savedRecipe.getIngredients().stream()
                                                .filter(recipeIngredients -> recipeIngredients.getDescription().equals(command.getDescription()))
                                                .filter(recipeIngredients -> recipeIngredients.getAmount().equals(command.getAmount()))
                                                .filter(recipeIngredients -> recipeIngredients.getUom().getId().equals(command.getUom().getId()))
                                                .findFirst();
                                    }

                                    log.error("Got ingredient saved");

                                    //todo check for fail

                                    //enhance with id value
                                    IngredientCommand ingredientCommandSaved = ingredientToIngredientCommand.convert(savedIngredientOptional.get());
                                    ingredientCommandSaved.setRecipeId(recipe.getId());
                                    log.error("ingredient to command");
                                    return ingredientCommandSaved;
                                }).toProcessor().block();
                    }
                });

    }

    public Mono<IngredientCommand> saveIngredientCommand_1(IngredientCommand command) {
        Optional<Recipe> recipeOptional = recipeReactiveRepository.findById(command.getRecipeId()).blockOptional();

        if (!recipeOptional.isPresent()) {

            //todo toss error if not found!
            log.error("Recipe not found for id: " + command.getRecipeId());
            return Mono.just(new IngredientCommand());
        } else {
            Recipe recipe = recipeOptional.get();

            Optional<Ingredient> ingredientOptional = recipe
                    .getIngredients()
                    .stream()
                    .filter(ingredient -> ingredient.getId().equals(command.getId()))
                    .findFirst();

            if (ingredientOptional.isPresent()) {
                Ingredient ingredientFound = ingredientOptional.get();
                ingredientFound.setDescription(command.getDescription());
                ingredientFound.setAmount(command.getAmount());
                ingredientFound.setUom(unitOfMeasureReactiveRepository
                        .findById(command.getUom().getId()).block());
                //        .orElseThrow(() -> new RuntimeException("UOM NOT FOUND"))); //todo address this
            } else {
                //add new Ingredient
                Ingredient ingredient = ingredientCommandToIngredient.convert(command);
                //  ingredient.setRecipe(recipe);
                recipe.addIngredient(ingredient);
            }

            Recipe savedRecipe = recipeReactiveRepository.save(recipe).block();

            Optional<Ingredient> savedIngredientOptional = savedRecipe.getIngredients().stream()
                    .filter(recipeIngredients -> recipeIngredients.getId().equals(command.getId()))
                    .findFirst();

            //check by description
            if (!savedIngredientOptional.isPresent()) {
                //not totally safe... But best guess
                savedIngredientOptional = savedRecipe.getIngredients().stream()
                        .filter(recipeIngredients -> recipeIngredients.getDescription().equals(command.getDescription()))
                        .filter(recipeIngredients -> recipeIngredients.getAmount().equals(command.getAmount()))
                        .filter(recipeIngredients -> recipeIngredients.getUom().getId().equals(command.getUom().getId()))
                        .findFirst();
            }

            //todo check for fail

            //enhance with id value
            IngredientCommand ingredientCommandSaved = ingredientToIngredientCommand.convert(savedIngredientOptional.get());
            ingredientCommandSaved.setRecipeId(recipe.getId());

            return Mono.just(ingredientCommandSaved);
        }

    }

    @Override
    public Mono<Void> deleteById(String recipeId, String idToDelete) {

        log.debug("Deleting ingredient: " + recipeId + ":" + idToDelete);

        Optional<Recipe> recipeOptional = recipeReactiveRepository.findById(recipeId).blockOptional();

        if (recipeOptional.isPresent()) {
            Recipe recipe = recipeOptional.get();
            log.debug("found recipe");

            Optional<Ingredient> ingredientOptional = recipe
                    .getIngredients()
                    .stream()
                    .filter(ingredient -> ingredient.getId().equals(idToDelete))
                    .findFirst();

            if (ingredientOptional.isPresent()) {
                log.debug("found Ingredient");

                recipe.getIngredients().remove(ingredientOptional.get());
                recipeReactiveRepository.save(recipe).block();
            }
        } else {
            log.debug("Recipe Id Not found. Id:" + recipeId);
        }
        return Mono.empty();
    }
}
