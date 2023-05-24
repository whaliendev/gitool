package edu.whu.gitool.model

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.Expose
import com.google.gson.annotations.JsonAdapter
import java.lang.reflect.Type

data class ConflictBlock(
    @Expose var index: Int = 0,
    var startLine: Int = 0, // start line of conflict block in conflict file
    var endLine: Int = 0,   // end line of conflict block in conflict file
    @Expose @JsonAdapter(ResolutionStrategySerializer::class)
    var strategy: ResolutionStrategy = ResolutionStrategy.UNKNOWN
) {
    @Expose
//    @JsonAdapter(CodeLinesSerializer::class)
    lateinit var ours: List<String>

    @Expose
//    @JsonAdapter(CodeLinesSerializer::class)
    lateinit var bases: List<String>

    @Expose
//    @JsonAdapter(CodeLinesSerializer::class)
    lateinit var theirs: List<String>

    @Expose
//    @JsonAdapter(CodeLinesSerializer::class)
    lateinit var merged: List<String>

    /**
     * Returns a string representation of the object.
     */
    override fun toString(): String {
        return "ConflictBlock(index=$index, startLine=$startLine, endLine=$endLine, ours=$ours, based=$bases, theirs=$theirs, merged=$merged)"
    }

    class CodeLinesSerializer : JsonSerializer<List<String>> {
        /**
         * Gson invokes this call-back method during serialization when it encounters a field of the
         * specified type.
         *
         *
         * In the implementation of this call-back method, you should consider invoking
         * [JsonSerializationContext.serialize] method to create JsonElements for any
         * non-trivial field of the `src` object. However, you should never invoke it on the
         * `src` object itself since that will cause an infinite loop (Gson will call your
         * call-back method again).
         *
         * @param src the object that needs to be converted to Json.
         * @param typeOfSrc the actual type (fully genericized version) of the source object.
         * @return a JsonElement corresponding to the specified object.
         */
        override fun serialize(
            src: List<String>,
            typeOfSrc: Type?,
            context: JsonSerializationContext
        ): JsonElement {
            val lines = src.joinToString("\n")
            return JsonPrimitive(lines)
        }

    }
}

