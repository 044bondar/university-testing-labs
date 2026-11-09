package com.recipebook;
public class TextRecipeExporter implements RecipeExporter {
    @Override
    public String export(Recipe recipe) {
        System.out.println("[STUB - TextRecipeExporter.export] Форматування рецепта в TXT: " + recipe.getTitle());
        StringBuilder sb = new StringBuilder();
        sb.append("--- РЕЦЕПТ: ").append(recipe.getTitle()).append(" ---\n");
        sb.append("Категорія: ").append(recipe.getCategory()).append("\n");
        sb.append("Час приготування: ").append(recipe.getCookingTimeMinutes()).append(" хв\n");
        sb.append("Інгредієнти:\n");
        for (Ingredient ing : recipe.getIngredients()) {
            sb.append(" - ").append(ing).append("\n");
        }
        sb.append("Загальна калорійність: ").append(recipe.calculateTotalCalories()).append(" ккал\n");
        return sb.toString();
    }
}