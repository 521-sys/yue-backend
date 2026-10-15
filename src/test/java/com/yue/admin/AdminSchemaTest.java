package com.yue.admin;

import com.yue.entity.Scene;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminSchemaTest {
    @Test void newScenesCannotAccidentallyPublish() throws Exception {
        Scene scene = Scene.builder().code("tea").title("点餐").build();
        assertEquals("DRAFT", property(scene, "getStatus"));
        assertEquals("日常生活", property(scene, "getCategory"));
        assertEquals("初级", property(scene, "getDifficulty"));
    }
    private Object property(Scene scene, String method) throws Exception {
        try { return scene.getClass().getMethod(method).invoke(scene); }
        catch (NoSuchMethodException missing) { return null; }
    }
}
