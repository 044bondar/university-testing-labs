package com.recipebook;

public class RecipePublishingService {
    private final RecipeExporter exporter;

    public RecipePublishingService(RecipeExporter exporter) {
        this.exporter = exporter;
    }

    public String publish(Recipe recipe) {
        System.out.println("[STUB - RecipePublishingService.publish] Посередник делегує форматування обраному експортеру");
        return exporter.export(recipe);
    }
}