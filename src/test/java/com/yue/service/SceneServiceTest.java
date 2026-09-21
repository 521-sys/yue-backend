package com.yue.service;

import com.yue.dto.SceneCreateRequest;
import com.yue.entity.Scene;
import com.yue.repository.SceneRepository;
import com.yue.repository.SceneLineRepository;
import com.yue.repository.UserSceneFavoriteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SceneServiceTest {
    @Mock SceneRepository scenes;
    @Mock SceneLineRepository lines;
    @Mock UserSceneFavoriteRepository favorites;
    @InjectMocks SceneService service;

    @Test
    void userCannotDeleteBuiltinOrAnotherUsersScene() {
        Scene builtin = Scene.builder().id(1L).builtin(true).build();
        when(scenes.findById(1L)).thenReturn(Optional.of(builtin));
        assertThrows(IllegalArgumentException.class, () -> service.delete(9L, 1L));
        verify(scenes, never()).delete(any());
    }

    @Test
    void favoriteIsIdempotent() {
        Scene scene = Scene.builder().id(2L).builtin(true).build();
        when(scenes.findById(2L)).thenReturn(Optional.of(scene));
        when(favorites.existsByUserIdAndSceneId(7L, 2L)).thenReturn(true);
        service.favorite(7L, 2L);
        verify(favorites, never()).save(any());
    }
}
