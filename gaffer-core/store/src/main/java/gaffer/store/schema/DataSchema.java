/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gaffer.store.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import gaffer.data.elementdefinition.ElementDefinitions;
import gaffer.data.elementdefinition.schema.exception.SchemaException;
import gaffer.serialisation.Serialisation;
import gaffer.serialisation.implementation.JavaSerialiser;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Contains the full list of {@link gaffer.data.element.Element} types to be stored in the graph.
 * <p>
 * Each type of element should have the identifier type(s) listed and a map of property names and their corresponding types.
 * Each type can either be a full java class name or a custom type. Using custom types then allows you to specify
 * validation and aggregation for the element components.
 * <p>
 * This class must be JSON serialisable.
 * A data schema should normally be written in JSON and then it will be automatically deserialised at runtime.
 * An example of a JSON data schemas can be found in the Example module.
 *
 * @see DataSchema.Builder
 * @see ElementDefinitions
 */
public class DataSchema extends ElementDefinitions<DataEntityDefinition, DataEdgeDefinition> {
    private static final Serialisation DEFAULT_VERTEX_SERIALISER = new JavaSerialiser();
    private static final long serialVersionUID = 4593579100621117269L;

    /**
     * The {@link gaffer.serialisation.Serialisation} for all identifiers. By default it is set to
     * {@link gaffer.serialisation.implementation.JavaSerialiser}.
     */
    private Serialisation vertexSerialiser = DEFAULT_VERTEX_SERIALISER;

    /**
     * A map of keys to positions.
     * This could be used to set the identifier, group or general property positions.
     */
    private Map<String, String> positions = new HashMap<>();
    /**
     * A map of custom type name to {@link TypeDefinition}.
     *
     * @see TypeDefinitions
     * @see TypeDefinition
     */
    private final TypeDefinitions types;

    public DataSchema() {
        this(new TypeDefinitions());
    }

    protected DataSchema(final TypeDefinitions types) {
        this.types = types;
    }

    public static DataSchema fromJson(final InputStream... inputStreams) throws SchemaException {
        return fromJson(DataSchema.class, inputStreams);
    }

    public static DataSchema fromJson(final Path... filePaths) throws SchemaException {
        return fromJson(DataSchema.class, filePaths);
    }

    public static DataSchema fromJson(final byte[]... jsonBytes) throws SchemaException {
        return fromJson(DataSchema.class, jsonBytes);
    }

    public TypeDefinitions getTypes() {
        return types;
    }

    /**
     * This does not override the current types it just appends the additional types.
     *
     * @param newTypes the new types to be added.
     */
    @JsonSetter("types")
    public void addTypes(final TypeDefinitions newTypes) {
        types.putAll(newTypes);
    }

    public void addType(final String typeName, final TypeDefinition type) {
        types.put(typeName, type);
    }

    public TypeDefinition getType(final String typeName) {
        return types.getType(typeName);
    }

    public String getPosition(final String key) {
        return positions.get(key);
    }

    /**
     * @return a map of keys to positions.
     * This could be used to set the identifier, group or general property positions.
     */
    public Map<String, String> getPositions() {
        return positions;
    }

    /**
     * @param positions a map of keys to positions.
     *                  This could be used to set the identifier, group or general property positions.
     */
    public void setPositions(final Map<String, String> positions) {
        this.positions = positions;
    }

    /**
     * Returns the vertex serialiser for this store schema.
     * <p>
     * There can be only one vertex serialiser per store schema because in order for searches to work correctly,
     * the byte representation of the search term's (seeds) must match the byte representation stored,
     * i.e you need to know how your results have been serialised which effectively means all vertices must be serialised the same way within a table.
     *
     * @return An implementation of {@link gaffer.serialisation.Serialisation} that will be used to serialise all vertices.
     */
    @JsonIgnore
    public Serialisation getVertexSerialiser() {
        return vertexSerialiser;
    }

    public void setVertexSerialiser(final Serialisation vertexSerialiser) {
        if (null != vertexSerialiser) {
            this.vertexSerialiser = vertexSerialiser;
        } else {
            this.vertexSerialiser = DEFAULT_VERTEX_SERIALISER;
        }
    }

    public String getVertexSerialiserClass() {
        final Class<? extends Serialisation> serialiserClass = vertexSerialiser.getClass();
        if (!DEFAULT_VERTEX_SERIALISER.getClass().equals(serialiserClass)) {
            return serialiserClass.getName();
        }

        return null;
    }

    public void setVertexSerialiserClass(final String vertexSerialiserClass) {
        if (null == vertexSerialiserClass) {
            this.vertexSerialiser = DEFAULT_VERTEX_SERIALISER;
        } else {
            Class<? extends Serialisation> serialiserClass;
            try {
                serialiserClass = Class.forName(vertexSerialiserClass).asSubclass(Serialisation.class);
            } catch (ClassNotFoundException e) {
                throw new SchemaException(e.getMessage(), e);
            }
            try {
                setVertexSerialiser(serialiserClass.newInstance());
            } catch (IllegalAccessException | IllegalArgumentException | SecurityException | InstantiationException e) {
                throw new SchemaException(e.getMessage(), e);
            }
        }
    }

    @Override
    public void setEdges(final Map<String, DataEdgeDefinition> edges) {
        super.setEdges(edges);
        for (DataElementDefinition def : edges.values()) {
            def.setTypesLookup(types);
        }
    }

    @Override
    public void setEntities(final Map<String, DataEntityDefinition> entities) {
        super.setEntities(entities);
        for (DataElementDefinition def : entities.values()) {
            def.setTypesLookup(types);
        }
    }

    @Override
    public DataElementDefinition getElement(final String group) {
        return (DataElementDefinition) super.getElement(group);
    }

    @Override
    public void merge(final ElementDefinitions<DataEntityDefinition, DataEdgeDefinition> elementDefs) {
        if (elementDefs instanceof DataSchema) {
            merge(((DataSchema) elementDefs));
        } else {
            super.merge(elementDefs);
        }
    }

    public void merge(final DataSchema dataSchema) {
        super.merge(dataSchema);

        for (Entry<String, String> entry : dataSchema.getPositions().entrySet()) {
            final String newPosKey = entry.getKey();
            final String newPosVal = entry.getValue();
            if (!positions.containsKey(newPosKey)) {
                positions.put(newPosKey, newPosVal);
            } else {
                final String posVal = positions.get(newPosKey);
                if (!posVal.equals(newPosVal)) {
                    throw new SchemaException("Unable to merge schemas. Conflict with position " + newPosKey
                            + ". Positions are: " + posVal + " and " + newPosVal);
                }
            }
        }

        if (DEFAULT_VERTEX_SERIALISER.getClass().equals(vertexSerialiser.getClass())) {
            setVertexSerialiser(dataSchema.getVertexSerialiser());
        } else if (!vertexSerialiser.getClass().equals(dataSchema.getVertexSerialiser().getClass())) {
            throw new SchemaException("Unable to merge schemas. Conflict with vertex serialiser, options are: "
                    + vertexSerialiser.getClass().getName() + " and " + dataSchema.getVertexSerialiser().getClass().getName());
        }

        types.merge(dataSchema.getTypes());
    }

    @Override
    protected void addEdge(final String group, final DataEdgeDefinition elementDef) {
        elementDef.setTypesLookup(types);
        super.addEdge(group, elementDef);
    }

    @Override
    protected void addEntity(final String group, final DataEntityDefinition elementDef) {
        elementDef.setTypesLookup(types);
        super.addEntity(group, elementDef);
    }

    public static class Builder extends ElementDefinitions.Builder<DataEntityDefinition, DataEdgeDefinition> {
        public Builder() {
            this(new DataSchema());
        }

        public Builder(final DataSchema dataSchema) {
            super(dataSchema);
        }

        /**
         * Adds a position for an identifier type, group or property name.
         *
         * @param key      the key to add a position for.
         * @param position the position
         * @return this Builder
         * @see gaffer.store.schema.DataSchema#setPositions(java.util.Map)
         */
        public Builder position(final String key, final String position) {
            Map<String, String> positions = getElementDefs().getPositions();
            if (null == positions) {
                positions = new HashMap<>();
                getElementDefs().setPositions(positions);
            }
            positions.put(key, position);

            return this;
        }

        /**
         * Sets the {@link gaffer.serialisation.Serialisation}.
         *
         * @param vertexSerialiser the {@link gaffer.serialisation.Serialisation} to set
         * @return this Builder
         * @see gaffer.store.schema.DataSchema#setVertexSerialiser(Serialisation)
         */
        public Builder vertexSerialiser(final Serialisation vertexSerialiser) {
            getElementDefs().setVertexSerialiser(vertexSerialiser);

            return this;
        }

        /**
         * Sets the {@link gaffer.serialisation.Serialisation} from class name.
         *
         * @param vertexSerialiserClass the {@link gaffer.serialisation.Serialisation} class name to set
         * @return this Builder
         * @see gaffer.store.schema.DataSchema#setVertexSerialiserClass(java.lang.String)
         */
        public Builder vertexSerialiser(final String vertexSerialiserClass) {
            getElementDefs().setVertexSerialiserClass(vertexSerialiserClass);

            return this;
        }

        @Override
        public Builder edge(final String group, final DataEdgeDefinition edgeDef) {
            return (Builder) super.edge(group, edgeDef);
        }

        public Builder edge(final String group) {
            return edge(group, new DataEdgeDefinition());
        }

        @Override
        public Builder entity(final String group, final DataEntityDefinition entityDef) {
            return (Builder) super.entity(group, entityDef);
        }

        public Builder entity(final String group) {
            return entity(group, new DataEntityDefinition());
        }

        public Builder type(final String typeName, final TypeDefinition type) {
            getElementDefs().addType(typeName, type);
            return this;
        }

        public Builder type(final String typeName, final Class<?> typeClass) {
            return type(typeName, new TypeDefinition(typeClass));
        }

        public Builder types(final TypeDefinitions types) {
            getElementDefs().addTypes(types);
            return this;
        }

        @Override
        public DataSchema build() {
            return (DataSchema) super.build();
        }

        @Override
        public DataSchema buildModule() {
            return (DataSchema) super.buildModule();
        }

        @Override
        protected DataSchema getElementDefs() {
            return (DataSchema) super.getElementDefs();
        }
    }
}