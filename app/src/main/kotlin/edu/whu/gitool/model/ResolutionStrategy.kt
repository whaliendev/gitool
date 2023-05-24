package edu.whu.gitool.model

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

enum class ResolutionStrategy(val literal: String) {
    OUR("our"),
    BASE("base"),
    THEIR("their"),
    CONCAT("concat"),
    DELETION("deletion"),
    INTERLEAVE("interleave"),

    // complex resolution strategy, cannot judge automatically
    NEWCODE("newcode"),
    UNRESOLVED("unresolved"),
    UNKNOWN("unknown")
}

class ResolutionStrategySerializer : JsonSerializer<ResolutionStrategy> {
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
        src: ResolutionStrategy,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        return JsonPrimitive(src.literal)
    }

}
