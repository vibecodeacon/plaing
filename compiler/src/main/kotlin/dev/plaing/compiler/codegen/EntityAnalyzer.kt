package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.*

data class AnalyzedEntity(
    val declaration: EntityDeclaration,
    val storedFields: List<FieldDefinition>,
    val foreignKeys: Map<String, String>,    // column_name -> referenced entity name
    val requiredFields: Set<String>,
    val uniqueFields: Set<String>,
    val hiddenFields: Set<String>,
    val defaults: Map<String, Expression>,
)

class EntityAnalyzer {
    fun analyze(entities: List<EntityDeclaration>): List<AnalyzedEntity> {
        val analyzed = entities.map { analyzeEntity(it) }
        return topologicalSort(analyzed)
    }

    private fun analyzeEntity(entity: EntityDeclaration): AnalyzedEntity {
        val storedFields = entity.fields.filter { TypeMapper.isStoredInTable(it) }

        val foreignKeys = mutableMapOf<String, String>()
        for (field in storedFields) {
            if (field.type is PlaingType.EntityRef) {
                foreignKeys[TypeMapper.toColumnName(field)] = (field.type as PlaingType.EntityRef).name
            }
        }

        val requiredFields = entity.fields
            .filter { TypeMapper.isRequired(it) }
            .map { it.name }
            .toSet()

        val uniqueFields = entity.fields
            .filter { TypeMapper.isUnique(it) }
            .map { it.name }
            .toSet()

        val hiddenFields = entity.fields
            .filter { TypeMapper.isHidden(it) }
            .map { it.name }
            .toSet()

        val defaults = mutableMapOf<String, Expression>()
        for (field in entity.fields) {
            val defaultValue = TypeMapper.getDefault(field)
            if (defaultValue != null) {
                defaults[field.name] = defaultValue
            }
        }

        return AnalyzedEntity(
            declaration = entity,
            storedFields = storedFields,
            foreignKeys = foreignKeys,
            requiredFields = requiredFields,
            uniqueFields = uniqueFields,
            hiddenFields = hiddenFields,
            defaults = defaults,
        )
    }

    /**
     * Topologically sort entities so that referenced tables come before referencing tables.
     * If A references B, B must come first in the list.
     */
    private fun topologicalSort(entities: List<AnalyzedEntity>): List<AnalyzedEntity> {
        val entityMap = entities.associateBy { it.declaration.name }
        val visited = mutableSetOf<String>()
        val result = mutableListOf<AnalyzedEntity>()

        fun visit(entity: AnalyzedEntity) {
            if (entity.declaration.name in visited) return
            visited.add(entity.declaration.name)
            // Visit dependencies first
            for ((_, refName) in entity.foreignKeys) {
                val ref = entityMap[refName]
                if (ref != null) {
                    visit(ref)
                }
            }
            result.add(entity)
        }

        for (entity in entities) {
            visit(entity)
        }
        return result
    }
}
