// SPDX-License-Identifier: Apache-2.0
package io.github.niwantha33.rew.core;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Rejects incomplete, inconsistent or duplicate catalog records. */
public final class BoardRegistry {
    private final Map<String, Board> boards;
    private BoardRegistry(Map<String, Board> entries) {
        boards = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }
    public static BoardRegistry load(Path path) throws IOException {
        Map<String, Board> entries = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] f = line.split("\\t", -1);
            if (f.length != 12)
                throw new IllegalArgumentException("Line " + (i + 1) + ": expected 12 fields");
            try {
                Board board = new Board(f[0], f[1], f[2], f[3], f[4], f[5],
                        Board.Toolchain.valueOf(f[6]), f[7], f[8],
                        Board.Evidence.valueOf(f[9]), URI.create(f[10]), f[11]);
                if (entries.putIfAbsent(board.id(), board) != null)
                    throw new IllegalArgumentException("Duplicate board: " + board.id());
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Line " + (i + 1) + ": " + error.getMessage(), error);
            }
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("Catalog is empty");
        return new BoardRegistry(entries);
    }
    public List<Board> all() { return List.copyOf(boards.values()); }
    public Board require(String id) {
        Board board = boards.get(id);
        if (board == null) throw new NoSuchElementException("Unknown board: " + id);
        return board;
    }
}
