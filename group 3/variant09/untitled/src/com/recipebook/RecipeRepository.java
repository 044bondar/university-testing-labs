package com.recipebook;

import java.util.ArrayList;
import java.util.List;

public class RecipeRepository {
    private final List<Recipe> databaseMock = new ArrayList<>();

    public void save(Recipe recipe) {
        System.out.println("[STUB - RecipeRepository.save] Збереження в базу даних рецепта: " + recipe.getTitle());
        databaseMock.add(recipe);
    }

    public List<Recipe> findAll() {
        System.out.println("[STUB - RecipeRepository.findAll] Запит усіх рецептів з бази даних");
        return new ArrayList<>(databaseMock);
    }
}