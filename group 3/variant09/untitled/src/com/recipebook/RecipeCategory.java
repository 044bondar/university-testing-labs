package com.recipebook;

public class RecipeCategory {
    private final String categoryName;

    public RecipeCategory(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategoryName() {
        return categoryName;
    }

    @Override
    public String toString() {
        return categoryName;
    }
}