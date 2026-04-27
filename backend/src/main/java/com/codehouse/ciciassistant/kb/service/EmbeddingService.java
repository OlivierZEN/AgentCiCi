package com.codehouse.ciciassistant.kb.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private final int dimension;

    public EmbeddingService(@Value("${app.kb.embedding.dimension:16}") int dimension) {
        this.dimension = Math.max(4, dimension);
    }

    public List<Float> embed(String text) {
        String input = text == null ? "" : text;
        float[] values = new float[dimension];
        for (int i = 0; i < input.length(); i++) {
            int slot = i % dimension;
            values[slot] += ((input.charAt(i) % 31) / 31.0f);
        }
        ArrayList<Float> out = new ArrayList<>(dimension);
        for (float v : values) {
            out.add(v);
        }
        return out;
    }
}
