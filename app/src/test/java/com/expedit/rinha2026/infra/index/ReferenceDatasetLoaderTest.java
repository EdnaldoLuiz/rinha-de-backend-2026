package com.expedit.rinha2026.infra.index;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReferenceDatasetLoaderTest {
    @Test
    void shouldParseLabelOrFraudKey() {
        String json = """
            [
              {"vector":[0,0,0,0,0,0,0,0,0,0,0,0,0,0],"label":"legit"},
              {"vector":[1,1,1,1,1,1,1,1,1,1,1,1,1,1],"label":"fraud"},
              {"vector":[0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5],"fraud":true}
            ]
            """;

        LoadedIndex index = new ReferenceDatasetLoader().load(json);

        assertEquals(3, index.labels().length);
        assertEquals(0, index.labels()[0]);
        assertEquals(1, index.labels()[1]);
        assertEquals(1, index.labels()[2]);
        assertEquals(3 * 16, index.vectors().length);
    }
}
