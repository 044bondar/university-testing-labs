package com.recipebook;

import java.util.ArrayList;
import java.util.List;

public class Cookbook {
    private final String name;
    private final List<Recipe> recipes = new ArrayList<>();

    public Cookbook(String name) {
        this.name = name;
    }

    public void addRecipe(Recipe recipe) {
        System.out.println("[STUB - Cookbook.addRecipe] Додано рецепт '" + recipe.getTitle() + "' до книги '" + name + "'");
        recipes.add(recipe);
    }

    public List<Recipe> getRecipes() {
        return new ArrayList<>(recipes);
    }

    public String getName() {
        return name;
    }
}