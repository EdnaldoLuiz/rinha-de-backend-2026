package com.expedit.rinha2026.infra.index;

import com.expedit.rinha2026.domain.Constants;
import java.util.ArrayList;
import java.util.List;

public final class ReferenceDatasetLoader {
    public LoadedIndex load(String json) {
        List<float[]> vectorsList = new ArrayList<>();
        List<Byte> labelsList = new ArrayList<>();

        int i = skipWs(json, 0);
        if (i >= json.length() || json.charAt(i) != '[') {
            throw new IllegalArgumentException("Invalid references JSON: expected array");
        }
        i++;

        while (i < json.length()) {
            i = skipWs(json, i);
            if (i >= json.length()) {
                break;
            }
            char c = json.charAt(i);
            if (c == ']') {
                break;
            }
            if (c == ',') {
                i++;
                continue;
            }
            if (c != '{') {
                throw new IllegalArgumentException("Invalid references JSON: expected object at index " + i);
            }

            ParsedRecord record = parseRecord(json, i);
            i = record.nextIndex;
            if (record.vector != null) {
                vectorsList.add(record.vector);
                labelsList.add(record.label);
            }
        }

        int n = vectorsList.size();
        float[] vectors = new float[n * Constants.VECTOR_STRIDE];
        byte[] labels = new byte[n];

        for (int row = 0; row < n; row++) {
            float[] src = vectorsList.get(row);
            int offset = row * Constants.VECTOR_STRIDE;
            System.arraycopy(src, 0, vectors, offset, Constants.VECTOR_DIMENSIONS);
            vectors[offset + 14] = 0f;
            vectors[offset + 15] = 0f;
            labels[row] = labelsList.get(row);
        }

        return LoadedIndex.fromVectors(vectors, labels);
    }

    private ParsedRecord parseRecord(String json, int objectStart) {
        int i = objectStart + 1;
        float[] vector = null;
        byte label = 0;

        while (i < json.length()) {
            i = skipWs(json, i);
            if (json.charAt(i) == '}') {
                return new ParsedRecord(vector, label, i + 1);
            }
            if (json.charAt(i) == ',') {
                i++;
                continue;
            }

            String key = parseString(json, i);
            i = nextAfterString(json, i);
            i = skipWs(json, i);
            if (json.charAt(i) != ':') {
                throw new IllegalArgumentException("Invalid references JSON: expected ':' after key " + key);
            }
            i++;
            i = skipWs(json, i);

            if ("vector".equals(key) || "features".equals(key) || "values".equals(key)) {
                ParsedArray arr = parseFloatArray(json, i);
                vector = arr.values;
                i = arr.nextIndex;
            } else if ("label".equals(key)) {
                String labelValue = parseString(json, i);
                label = toLabel(labelValue);
                i = nextAfterString(json, i);
            } else if ("fraud".equals(key)) {
                if (json.startsWith("true", i)) {
                    label = 1;
                    i += 4;
                } else if (json.startsWith("false", i)) {
                    label = 0;
                    i += 5;
                } else {
                    throw new IllegalArgumentException("Invalid boolean for fraud at index " + i);
                }
            } else {
                i = skipValue(json, i);
            }
        }

        throw new IllegalArgumentException("Invalid references JSON: unterminated object");
    }

    private ParsedArray parseFloatArray(String json, int start) {
        int i = skipWs(json, start);
        if (json.charAt(i) != '[') {
            throw new IllegalArgumentException("Invalid vector: expected array at index " + i);
        }
        i++;

        float[] values = new float[Constants.VECTOR_DIMENSIONS];
        int count = 0;

        while (i < json.length()) {
            i = skipWs(json, i);
            char c = json.charAt(i);
            if (c == ']') {
                i++;
                break;
            }
            if (c == ',') {
                i++;
                continue;
            }

            int end = findNumberEnd(json, i);
            if (count < Constants.VECTOR_DIMENSIONS) {
                values[count] = Float.parseFloat(json.substring(i, end));
            }
            count++;
            i = end;
        }

        if (count < Constants.VECTOR_DIMENSIONS) {
            throw new IllegalArgumentException("Vector with fewer than 14 dimensions");
        }

        return new ParsedArray(values, i);
    }

    private int skipValue(String json, int start) {
        int i = skipWs(json, start);
        char c = json.charAt(i);

        if (c == '"') {
            return nextAfterString(json, i);
        }
        if (c == '{') {
            return skipContainer(json, i, '{', '}');
        }
        if (c == '[') {
            return skipContainer(json, i, '[', ']');
        }

        while (i < json.length()) {
            c = json.charAt(i);
            if (c == ',' || c == '}' || c == ']') {
                return i;
            }
            i++;
        }
        return i;
    }

    private int skipContainer(String json, int start, char open, char close) {
        int depth = 0;
        boolean inString = false;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && !isEscaped(json, i)) {
                inString = !inString;
            }
            if (inString) {
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }

        throw new IllegalArgumentException("Invalid JSON: unterminated container");
    }

    private int findNumberEnd(String json, int start) {
        int i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                i++;
                continue;
            }
            break;
        }
        return i;
    }

    private String parseString(String json, int quoteStart) {
        if (json.charAt(quoteStart) != '"') {
            throw new IllegalArgumentException("Invalid JSON string start at index " + quoteStart);
        }
        int end = quoteStart + 1;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && !isEscaped(json, end)) {
                return json.substring(quoteStart + 1, end);
            }
            end++;
        }
        throw new IllegalArgumentException("Unterminated JSON string");
    }

    private int nextAfterString(String json, int quoteStart) {
        int end = quoteStart + 1;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && !isEscaped(json, end)) {
                return end + 1;
            }
            end++;
        }
        throw new IllegalArgumentException("Unterminated JSON string");
    }

    private boolean isEscaped(String text, int index) {
        int backslashes = 0;
        int i = index - 1;
        while (i >= 0 && text.charAt(i) == '\\') {
            backslashes++;
            i--;
        }
        return (backslashes % 2) == 1;
    }

    private int skipWs(String json, int start) {
        int i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                i++;
                continue;
            }
            return i;
        }
        return i;
    }

    private byte toLabel(String label) {
        if ("fraud".equalsIgnoreCase(label) || "1".equals(label) || "true".equalsIgnoreCase(label)) {
            return 1;
        }
        return 0;
    }

    private record ParsedArray(float[] values, int nextIndex) {
    }

    private record ParsedRecord(float[] vector, byte label, int nextIndex) {
    }
}
