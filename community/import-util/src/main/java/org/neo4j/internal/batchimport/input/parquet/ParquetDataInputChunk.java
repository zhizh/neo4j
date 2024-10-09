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

import java.io.IOException;
import org.neo4j.batchimport.api.input.InputEntityVisitor;

/**
 * The data chunk to be stuck to a Parquet reader.
 * One chunk equals one file.
 */
class ParquetDataInputChunk implements ParquetInputChunk {

    private ParquetDataReader reader;

    @Override
    public boolean next(InputEntityVisitor visitor) throws IOException {
        if (reader == null) {
            return false;
        }
        return reader.next(visitor);
    }

    @Override
    public boolean readWith(ParquetDataReader reader) {
        this.reader = reader;
        return reader.hasNext();
    }

    @Override
    public void close() throws IOException {}
}
