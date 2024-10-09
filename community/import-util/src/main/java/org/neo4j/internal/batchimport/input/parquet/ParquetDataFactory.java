/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.internal.batchimport.input.parquet;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Handles the iteration through multiple files for a particular set of labels or a relationship type.
 */
public final class ParquetDataFactory {

    private static final Supplier<ZoneId> defaultTimezoneSupplier = () -> ZoneOffset.UTC;
    private final Iterator<Map.Entry<Set<String>, List<Path>>> labelsOrTypeAndFilesIterator;

    private Iterator<Path> currentFileIterator;
    private Map.Entry<Set<String>, List<Path>> currentLabelsOrTypeAndFiles;
    /**
     *
     */
    public ParquetDataFactory(Map<Set<String>, List<Path>> labelsOrTypeAndFiles) {
        this.labelsOrTypeAndFilesIterator = labelsOrTypeAndFiles.entrySet().iterator();
    }

    ParquetData getNextFile() {
        if (currentFileIterator == null || !currentFileIterator.hasNext()) {
            currentLabelsOrTypeAndFiles = labelsOrTypeAndFilesIterator.next();
            currentFileIterator = currentLabelsOrTypeAndFiles.getValue().iterator();
        }
        return new ParquetData(
                currentLabelsOrTypeAndFiles.getKey(), currentFileIterator.next(), defaultTimezoneSupplier);
    }

    public boolean hasNextFile() {
        if (currentFileIterator == null
                && currentLabelsOrTypeAndFiles == null
                && !labelsOrTypeAndFilesIterator.hasNext()) {
            return false;
        }
        if (currentLabelsOrTypeAndFiles == null && labelsOrTypeAndFilesIterator.hasNext()) {
            currentLabelsOrTypeAndFiles = labelsOrTypeAndFilesIterator.next();
        }
        if (currentFileIterator == null) {
            currentFileIterator = currentLabelsOrTypeAndFiles.getValue().iterator();
        }
        if (!currentFileIterator.hasNext()) {
            return labelsOrTypeAndFilesIterator.hasNext();
        }
        return true;
    }
}
