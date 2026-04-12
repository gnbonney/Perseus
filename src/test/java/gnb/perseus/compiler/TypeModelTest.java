package gnb.perseus.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TypeModelTest {

    @Test
    void legacy_scalar_and_reference_types_round_trip() {
        assertEquals(Type.INTEGER, Type.parse("integer"));
        assertEquals(Type.ref("Point"), Type.parse("ref:Point"));
        assertEquals("ref:Point", Type.ref("Point").toLegacyString());
    }

    @Test
    void legacy_collection_and_procedure_types_round_trip() {
        Type vector = Type.parse("vector:integer");
        Type map = Type.parse("map:string=>real");
        Type procedure = Type.parse("procedure:ref:Point");

        assertTrue(vector.isVector());
        assertEquals(Type.INTEGER, vector.elementType());
        assertEquals("map:string=>real", map.toLegacyString());
        assertTrue(procedure.isProcedure());
        assertEquals(Type.ref("Point"), procedure.elementType());
    }

    @Test
    void legacy_wrapper_types_round_trip() {
        Type thunk = Type.parse("thunk:real");
        Type array = Type.parse("ref:Point[]");
        Type iterable = Type.parse("iterable:string");

        assertTrue(thunk.isThunk());
        assertEquals(Type.REAL, thunk.unwrapThunk());
        assertTrue(array.isArray());
        assertEquals(Type.ref("Point"), array.elementType());
        assertEquals("iterable:string", iterable.toLegacyString());
    }
}
