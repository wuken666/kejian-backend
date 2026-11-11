package com.teacherfeedback.api.service;

import java.util.Locale;

public final class CharacterErrorRate {
    private CharacterErrorRate() {
    }

    // 统一中英文评测口径：忽略空白、标点和英文字母大小写。
    public static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
            .codePoints()
            .filter(Character::isLetterOrDigit)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    }

    // 使用动态规划计算字符级替换、漏字和多字数量。
    public static Result compare(String referenceText, String recognizedText) {
        int[] reference = normalize(referenceText).codePoints().toArray();
        int[] recognized = normalize(recognizedText).codePoints().toArray();
        Cell[][] table = new Cell[reference.length + 1][recognized.length + 1];
        table[0][0] = new Cell(0, 0, 0);
        for (int row = 1; row <= reference.length; row++) {
            table[row][0] = new Cell(0, row, 0);
        }
        for (int column = 1; column <= recognized.length; column++) {
            table[0][column] = new Cell(0, 0, column);
        }

        for (int row = 1; row <= reference.length; row++) {
            for (int column = 1; column <= recognized.length; column++) {
                if (reference[row - 1] == recognized[column - 1]) {
                    table[row][column] = table[row - 1][column - 1];
                    continue;
                }
                Cell substitution = table[row - 1][column - 1].addSubstitution();
                Cell deletion = table[row - 1][column].addDeletion();
                Cell insertion = table[row][column - 1].addInsertion();
                table[row][column] = minimum(substitution, deletion, insertion);
            }
        }

        Cell result = table[reference.length][recognized.length];
        double rate = reference.length == 0
            ? (recognized.length == 0 ? 0 : 1)
            : (double) result.errors() / reference.length;
        return new Result(
            reference.length,
            recognized.length,
            result.substitutions(),
            result.deletions(),
            result.insertions(),
            result.errors(),
            rate
        );
    }

    private static Cell minimum(Cell first, Cell second, Cell third) {
        Cell best = first;
        if (second.errors() < best.errors()) best = second;
        if (third.errors() < best.errors()) best = third;
        return best;
    }

    private record Cell(int substitutions, int deletions, int insertions) {
        int errors() {
            return substitutions + deletions + insertions;
        }

        Cell addSubstitution() {
            return new Cell(substitutions + 1, deletions, insertions);
        }

        Cell addDeletion() {
            return new Cell(substitutions, deletions + 1, insertions);
        }

        Cell addInsertion() {
            return new Cell(substitutions, deletions, insertions + 1);
        }
    }

    public record Result(
        int referenceCharacters,
        int recognizedCharacters,
        int substitutions,
        int deletions,
        int insertions,
        int errors,
        double rate
    ) {
    }
}
