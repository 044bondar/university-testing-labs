package com.recipebook;

public class main {
    public static void main(String[] args) {
        System.out.println("=== ДЕМОНСТРАЦІЯ GRASP: КНИГА КУЛІНАРНИХ РЕЦЕПТІВ ===\n");

        Cookbook cookbook = new Cookbook("Улюблені домашні страви");
        RecipeRepository repository = new RecipeRepository();
        CookbookController controller = new CookbookController(cookbook, repository);

        Recipe borsch = controller.handleCreateRecipe("Український борщ", 90, "Перші страви");

        controller.handleAddIngredient(borsch, "Буряк", 300, 43.0);
        controller.handleAddIngredient(borsch, "Картопля", 400, 77.0);
        controller.handleAddIngredient(borsch, "Яловичина", 500, 250.0);

        System.out.println("\n--- Результат підрахунку Information Expert ---");
        System.out.println("Загальна калорійність страви '" + borsch.getTitle() + "': "
                + borsch.calculateTotalCalories() + " ккал\n");

        RecipeExporter textExporter = new TextRecipeExporter();
        RecipePublishingService textPublishingService = new RecipePublishingService(textExporter);
        String textOutput = controller.handleExportRecipe(borsch, textPublishingService);
        System.out.println(textOutput);

        RecipeExporter jsonExporter = new JsonRecipeExporter();
        RecipePublishingService jsonPublishingService = new RecipePublishingService(jsonExporter);
        String jsonOutput = controller.handleExportRecipe(borsch, jsonPublishingService);
        System.out.println(jsonOutput);

        System.out.println("\n=== РОБОТУ ПРОГРАМИ УСПІШНО ЗАВЕРШЕНО ===");
    }
}