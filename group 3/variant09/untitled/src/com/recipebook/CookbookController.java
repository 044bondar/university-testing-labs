package com.recipebook;

public class CookbookController {
    private final Cookbook cookbook;
    private final RecipeRepository repository;

    public CookbookController(Cookbook cookbook, RecipeRepository repository) {
        this.cookbook = cookbook;
        this.repository = repository;
    }

    public Recipe handleCreateRecipe(String title, int timeMinutes, String categoryName) {
        System.out.println("[STUB - Controller.handleCreateRecipe] Обробка запиту створення рецепта: " + title);
        RecipeCategory category = new RecipeCategory(categoryName);
        Recipe recipe = new Recipe(title, timeMinutes, category);
        cookbook.addRecipe(recipe);
        repository.save(recipe);
        return recipe;
    }

    public void handleAddIngredient(Recipe recipe, String name, double amountGrams, double caloriesPer100g) {
        System.out.println("[STUB - Controller.handleAddIngredient] Запит на додавання " + name);
        recipe.addIngredient(name, amountGrams, caloriesPer100g);
    }

    public String handleExportRecipe(Recipe recipe, RecipePublishingService service) {
        System.out.println("[STUB - Controller.handleExportRecipe] Запит на публікацію рецепта");
        return service.publish(recipe);
    }
}