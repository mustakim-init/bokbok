@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mustakim.bokbok.music.ui.screens.settings
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR
import com.mustakim.bokbok.data.local.*
import kotlinx.coroutines.flow.first

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mustakim.bokbok.ui.shared.LocalPlayerAwareWindowInsets

import com.mustakim.bokbok.music.constants.ChipSortTypeKey
import com.mustakim.bokbok.music.constants.DarkModeKey
import com.mustakim.bokbok.music.constants.DefaultOpenTabKey
import com.mustakim.bokbok.music.constants.DynamicThemeKey
import com.mustakim.bokbok.music.constants.GridItemSize
import com.mustakim.bokbok.music.constants.GridItemsSizeKey
import com.mustakim.bokbok.music.constants.LibraryFilter
import com.mustakim.bokbok.music.constants.LyricsClickKey
import com.mustakim.bokbok.music.constants.LyricsScrollKey
import com.mustakim.bokbok.music.constants.LyricsTextPositionKey
import com.mustakim.bokbok.music.constants.PlayerDesignStyle
import com.mustakim.bokbok.music.constants.PlayerDesignStyleKey
import com.mustakim.bokbok.music.constants.PlayerBackgroundStyle
import com.mustakim.bokbok.music.constants.PlayerBackgroundStyleKey
import com.mustakim.bokbok.music.constants.PureBlackKey
import com.mustakim.bokbok.music.constants.RandomThemeOnStartupKey
import com.mustakim.bokbok.music.constants.UseSystemFontKey
import com.mustakim.bokbok.music.constants.PlayerButtonsStyle
import com.mustakim.bokbok.music.constants.PlayerButtonsStyleKey
import com.mustakim.bokbok.music.constants.LyricsAnimationStyleKey
import com.mustakim.bokbok.music.constants.LyricsAnimationStyle
import com.mustakim.bokbok.music.constants.LyricsTextSizeKey
import com.mustakim.bokbok.music.constants.LyricsLineSpacingKey
import com.mustakim.bokbok.music.constants.SliderStyle
import com.mustakim.bokbok.music.constants.SliderStyleKey
import com.mustakim.bokbok.music.constants.ShowLikedPlaylistKey
import com.mustakim.bokbok.music.constants.ShowDownloadedPlaylistKey
import com.mustakim.bokbok.music.constants.ShowHomeCategoryChipsKey
import com.mustakim.bokbok.music.constants.ShowTopPlaylistKey
import com.mustakim.bokbok.music.constants.ShowCachedPlaylistKey
import com.mustakim.bokbok.music.constants.ShowTagsInLibraryKey
import com.mustakim.bokbok.music.constants.SwipeThumbnailKey
import com.mustakim.bokbok.music.constants.SwipeSensitivityKey
import com.mustakim.bokbok.music.constants.SwipeToSongKey
import com.mustakim.bokbok.music.constants.HidePlayerThumbnailKey
import com.mustakim.bokbok.music.constants.BokBokCanvasKey
import com.mustakim.bokbok.music.constants.ThumbnailCornerRadiusKey
import com.mustakim.bokbok.music.constants.CropThumbnailToSquareKey
import com.mustakim.bokbok.music.constants.DisableBlurKey
import com.mustakim.bokbok.music.constants.BlurRadiusKey
import com.mustakim.bokbok.music.constants.UseLyricsV2Key
import com.mustakim.bokbok.ui.shared.DefaultDialog
import com.mustakim.bokbok.music.ui.component.EnumListPreference
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.music.ui.component.ListPreference
import com.mustakim.bokbok.music.ui.component.PreferenceEntry
import com.mustakim.bokbok.music.ui.component.PreferenceGroupTitle
import com.mustakim.bokbok.music.ui.component.SwitchPreference
import com.mustakim.bokbok.music.ui.component.ThumbnailCornerRadiusSelectorButton
import com.mustakim.bokbok.music.ui.player.StyledPlaybackSlider
import com.mustakim.bokbok.music.ui.utils.backToMain
import com.mustakim.bokbok.data.local.rememberEnumPreference
import com.mustakim.bokbok.data.local.rememberPreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (dynamicTheme, onDynamicThemeChange) = rememberPreference(
        DynamicThemeKey,
        defaultValue = true
    )
    val (randomThemeOnStartup, onRandomThemeOnStartupChange) = rememberPreference(
        RandomThemeOnStartupKey,
        defaultValue = false
    )
    val (darkMode, onDarkModeChange) = rememberEnumPreference(
        DarkModeKey,
        defaultValue = DarkMode.AUTO
    )
    val (playerDesignStyle, onPlayerDesignStyleChange) = rememberEnumPreference(
        PlayerDesignStyleKey,
        defaultValue = PlayerDesignStyle.V4
    )
    val (useNewLibraryDesign, onUseNewLibraryDesignChange) = rememberPreference(
        key = com.mustakim.bokbok.music.constants.UseNewLibraryDesignKey,
        defaultValue = false
    )
    val (hidePlayerThumbnail, onHidePlayerThumbnailChange) = rememberPreference(
        HidePlayerThumbnailKey,
        defaultValue = false
    )
    val (bokbokCanvasEnabled, onBokBokCanvasEnabledChange) = rememberPreference(
        BokBokCanvasKey,
        defaultValue = false
    )
    val (thumbnailCornerRadius, onThumbnailCornerRadiusChange) = rememberPreference(
        key = ThumbnailCornerRadiusKey,
        defaultValue = 16f // default dp
    )
    val (cropThumbnailToSquare, onCropThumbnailToSquareChange) = rememberPreference(
        CropThumbnailToSquareKey,
        defaultValue = false
    )
    val (playerBackground, onPlayerBackgroundChange) =
        rememberEnumPreference(
            PlayerBackgroundStyleKey,
            defaultValue = PlayerBackgroundStyle.DEFAULT,
        )
    val (pureBlack, onPureBlackChange) = rememberPreference(PureBlackKey, defaultValue = false)
    val (disableBlur, onDisableBlurChange) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (blurRadius, onBlurRadiusChange) = rememberPreference(BlurRadiusKey, defaultValue = 36f)
    val (useSystemFont, onUseSystemFontChange) = rememberPreference(UseSystemFontKey, defaultValue = false)
    val (defaultOpenTab, onDefaultOpenTabChange) = rememberEnumPreference(
        DefaultOpenTabKey,
        defaultValue = NavigationTab.HOME
    )
    val (playerButtonsStyle, onPlayerButtonsStyleChange) = rememberEnumPreference(
        PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT
    )
    val (lyricsPosition, onLyricsPositionChange) = rememberEnumPreference(
        LyricsTextPositionKey,
        defaultValue = LyricsPosition.LEFT
    )
    val (lyricsAnimation, onLyricsAnimationChange) = rememberEnumPreference<LyricsAnimationStyle>(
    key = LyricsAnimationStyleKey,
    defaultValue = LyricsAnimationStyle.APPLE
    )
    val (lyricsClick, onLyricsClickChange) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsScroll, onLyricsScrollChange) = rememberPreference(LyricsScrollKey, defaultValue = true)
    val (lyricsTextSize, onLyricsTextSizeChange) = rememberPreference(LyricsTextSizeKey, defaultValue = 26f)
    val (lyricsLineSpacing, onLyricsLineSpacingChange) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)
    val (useLyricsV2, onUseLyricsV2Change) = rememberPreference(UseLyricsV2Key, defaultValue = false)

    val (sliderStyle, onSliderStyleChange) = rememberEnumPreference(
        SliderStyleKey,
        defaultValue = SliderStyle.Standard
    )
    val (swipeThumbnail, onSwipeThumbnailChange) = rememberPreference(
        SwipeThumbnailKey,
        defaultValue = true
    )
    val (swipeSensitivity, onSwipeSensitivityChange) = rememberPreference(
        SwipeSensitivityKey,
        defaultValue = 0.73f
    )
    val (gridItemSize, onGridItemSizeChange) = rememberEnumPreference(
        GridItemsSizeKey,
        defaultValue = GridItemSize.SMALL
    )

    val (swipeToSong, onSwipeToSongChange) = rememberPreference(
        SwipeToSongKey,
        defaultValue = false
    )

    val (showLikedPlaylist, onShowLikedPlaylistChange) = rememberPreference(
        ShowLikedPlaylistKey,
        defaultValue = true
    )
    val (showDownloadedPlaylist, onShowDownloadedPlaylistChange) = rememberPreference(
        ShowDownloadedPlaylistKey,
        defaultValue = true
    )
    val (showTopPlaylist, onShowTopPlaylistChange) = rememberPreference(
        ShowTopPlaylistKey,
        defaultValue = true
    )
    val (showCachedPlaylist, onShowCachedPlaylistChange) = rememberPreference(
        ShowCachedPlaylistKey,
        defaultValue = true
    )
    val (showTagsInLibrary, onShowTagsInLibraryChange) = rememberPreference(
        ShowTagsInLibraryKey,
        defaultValue = true
    )
    val (showHomeCategoryChips, onShowHomeCategoryChipsChange) = rememberPreference(
        ShowHomeCategoryChipsKey,
        defaultValue = true
    )

    val availableBackgroundStyles = PlayerBackgroundStyle.entries.filter {
        it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
    val isBokBokCanvasAvailable = playerDesignStyle != PlayerDesignStyle.V7

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme =
        remember(darkMode, isSystemInDarkTheme) {
            if (darkMode == DarkMode.AUTO) isSystemInDarkTheme else darkMode == DarkMode.ON
        }

    val (defaultChip, onDefaultChipChange) = rememberEnumPreference(
        key = ChipSortTypeKey,
        defaultValue = LibraryFilter.LIBRARY
    )

    var showSliderOptionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSliderOptionDialog) {
        val sliderStyles = remember {
            listOf(
                SliderStyle.Standard,
                SliderStyle.Wavy,
                SliderStyle.Thick,
                SliderStyle.Circular,
                SliderStyle.Simple
            )
        }
        DefaultDialog(
            buttons = {
                TextButton(
                    onClick = { showSliderOptionDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
            onDismiss = {
                showSliderOptionDialog = false
            }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sliderStyles.chunked(3).forEach { styleRow ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        styleRow.forEach { style ->
                            SliderStyleOptionCard(
                                sliderStyle = style,
                                selected = sliderStyle == style,
                                onClick = {
                                    onSliderStyleChange(style)
                                    showSliderOptionDialog = false
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - styleRow.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        PreferenceGroupTitle(
            title = stringResource(MusicR.string.theme),
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.enable_dynamic_theme)) },
            icon = { Icon(painterResource(CoreR.drawable.palette), null) },
            checked = dynamicTheme,
            onCheckedChange = onDynamicThemeChange,
        )

        AnimatedVisibility(visible = !dynamicTheme || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            SwitchPreference(
                title = { Text(stringResource(MusicR.string.random_theme_on_startup)) },
                description = stringResource(MusicR.string.random_theme_on_startup_desc),
                icon = { Icon(painterResource(CoreR.drawable.shuffle), null) },
                checked = randomThemeOnStartup,
                onCheckedChange = onRandomThemeOnStartupChange,
            )
        }

        AnimatedVisibility(visible = !dynamicTheme || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            PreferenceEntry(
                title = { Text(stringResource(MusicR.string.color_palette)) },
                description = stringResource(MusicR.string.customize_theme_colors),
                icon = { Icon(painterResource(CoreR.drawable.format_paint), null) },
                onClick = { navController.navigate("settings/appearance/palette_picker") }
            )
        }

        EnumListPreference(
            title = { Text(stringResource(MusicR.string.dark_theme)) },
            icon = { Icon(painterResource(CoreR.drawable.dark_mode), null) },
            selectedValue = darkMode,
            onValueSelected = onDarkModeChange,
            valueText = {
                when (it) {
                    DarkMode.ON -> stringResource(MusicR.string.dark_theme_on)
                    DarkMode.OFF -> stringResource(MusicR.string.dark_theme_off)
                    DarkMode.AUTO -> stringResource(MusicR.string.dark_theme_follow_system)
                }
            },
        )

        AnimatedVisibility(useDarkTheme) {
            SwitchPreference(
                title = { Text(stringResource(MusicR.string.pure_black)) },
                icon = { Icon(painterResource(CoreR.drawable.contrast), null) },
                checked = pureBlack,
                onCheckedChange = onPureBlackChange,
            )
        }

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.disable_blur)) },
            description = stringResource(MusicR.string.disable_blur_desc),
            icon = { Icon(painterResource(CoreR.drawable.blur_off), null) },
            checked = disableBlur,
            onCheckedChange = onDisableBlurChange,
        )

        PreferenceEntry(
            title = { Text(stringResource(MusicR.string.blur_intensity)) },
            description = stringResource(MusicR.string.blur_intensity_value, blurRadius.roundToInt()),
            icon = { Icon(painterResource(CoreR.drawable.blur_on), null) },
            isEnabled = !disableBlur,
            content = {
                Spacer(modifier = Modifier.height(10.dp))
                Slider(
                    value = blurRadius,
                    onValueChange = onBlurRadiusChange,
                    valueRange = 0f..48f,
                    steps = 47,
                    enabled = !disableBlur,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.use_system_font)) },
            description = stringResource(MusicR.string.use_system_font_desc),
            icon = { Icon(painterResource(CoreR.drawable.text_fields), null) },
            checked = useSystemFont,
            onCheckedChange = onUseSystemFontChange,
        )

        PreferenceGroupTitle(
            title = stringResource(MusicR.string.player),
        )

        EnumListPreference(
            title = { Text(stringResource(MusicR.string.player_design_style)) },
            icon = { Icon(painterResource(CoreR.drawable.palette), null) },
            selectedValue = playerDesignStyle,
            onValueSelected = onPlayerDesignStyleChange,
            valueText = {
                when (it) {
                    PlayerDesignStyle.V1 -> stringResource(MusicR.string.player_design_v1)
                    PlayerDesignStyle.V2 -> stringResource(MusicR.string.player_design_v2)
                    PlayerDesignStyle.V3 -> stringResource(MusicR.string.player_design_v3)
                    PlayerDesignStyle.V4 -> stringResource(MusicR.string.player_design_v4)
                    PlayerDesignStyle.V5 -> stringResource(MusicR.string.player_design_v5)
                    PlayerDesignStyle.V6 -> stringResource(MusicR.string.player_design_v6)
                    PlayerDesignStyle.V7 -> stringResource(MusicR.string.player_design_v7)
                }
            },
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.new_library_design)) },
            description = stringResource(MusicR.string.new_library_design_description),
            icon = { Icon(painterResource(CoreR.drawable.grid_view), null) },
            checked = useNewLibraryDesign,
            onCheckedChange = onUseNewLibraryDesignChange,
        )

        EnumListPreference(
            title = { Text(stringResource(MusicR.string.player_background_style)) },
            icon = { Icon(painterResource(CoreR.drawable.gradient), null) },
            selectedValue = playerBackground,
            onValueSelected = onPlayerBackgroundChange,
            valueText = {
                when (it) {
                    PlayerBackgroundStyle.DEFAULT -> stringResource(MusicR.string.follow_theme)
                    PlayerBackgroundStyle.GRADIENT -> stringResource(MusicR.string.gradient)
                        PlayerBackgroundStyle.CUSTOM -> stringResource(MusicR.string.custom)
                    PlayerBackgroundStyle.BLUR -> stringResource(MusicR.string.player_background_blur)
                    PlayerBackgroundStyle.COLORING -> stringResource(MusicR.string.coloring)
                    PlayerBackgroundStyle.BLUR_GRADIENT -> stringResource(MusicR.string.blur_gradient)
                    PlayerBackgroundStyle.GLOW -> stringResource(MusicR.string.glow)
                    PlayerBackgroundStyle.GLOW_ANIMATED -> "Glow Animated"
                }
            },
        )

        // When custom background is selected, show a direct link to customize it
        if (playerBackground == PlayerBackgroundStyle.CUSTOM) {
            PreferenceEntry(
                title = { Text(stringResource(MusicR.string.customized_background)) },
                icon = { Icon(painterResource(CoreR.drawable.image), null) },
                onClick = { navController.navigate("customize_background") }
            )
        }

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.hide_player_thumbnail)) },
            description = stringResource(MusicR.string.hide_player_thumbnail_desc),
            icon = { Icon(painterResource(CoreR.drawable.hide_image), null) },
            checked = hidePlayerThumbnail,
            onCheckedChange = onHidePlayerThumbnailChange
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.bokbok_canvas)) },
            description = if (isBokBokCanvasAvailable) {
                stringResource(MusicR.string.bokbok_canvas_desc)
            } else {
                stringResource(MusicR.string.bokbok_canvas_v7_desc)
            },
            icon = { Icon(painterResource(CoreR.drawable.motion_photos_on), null) },
            checked = bokbokCanvasEnabled && isBokBokCanvasAvailable,
            onCheckedChange = onBokBokCanvasEnabledChange,
            isEnabled = isBokBokCanvasAvailable,
        )
      

        ThumbnailCornerRadiusSelectorButton(
            onRadiusSelected = {}
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.crop_thumbnail_to_square)) },
            description = stringResource(MusicR.string.crop_thumbnail_to_square_desc),
            icon = { Icon(painterResource(CoreR.drawable.image), null) },
            checked = cropThumbnailToSquare,
            onCheckedChange = onCropThumbnailToSquareChange
        )


        EnumListPreference(
            title = { Text(stringResource(MusicR.string.player_buttons_style)) },
            icon = { Icon(painterResource(CoreR.drawable.palette), null) },
            selectedValue = playerButtonsStyle,
            onValueSelected = onPlayerButtonsStyleChange,
            valueText = {
                when (it) {
                    PlayerButtonsStyle.DEFAULT -> stringResource(MusicR.string.default_style)
                    PlayerButtonsStyle.SECONDARY -> stringResource(MusicR.string.secondary_color_style)
                }
            },
        )

        PreferenceEntry(
            title = { Text(stringResource(MusicR.string.player_slider_style)) },
            description = sliderStyleLabel(sliderStyle),
            icon = { Icon(painterResource(CoreR.drawable.sliders), null) },
            onClick = {
                showSliderOptionDialog = true
            },
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.enable_swipe_thumbnail)) },
            icon = { Icon(painterResource(CoreR.drawable.swipe), null) },
            checked = swipeThumbnail,
            onCheckedChange = onSwipeThumbnailChange,
        )

        AnimatedVisibility(swipeThumbnail) {
            var showSensitivityDialog by rememberSaveable { mutableStateOf(false) }
            
            if (showSensitivityDialog) {
                var tempSensitivity by remember { mutableFloatStateOf(swipeSensitivity) }
                
                DefaultDialog(
                    onDismiss = { 
                        tempSensitivity = swipeSensitivity
                        showSensitivityDialog = false 
                    },
                    buttons = {
                        TextButton(
                            onClick = { 
                                tempSensitivity = 0.73f
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(MusicR.string.reset))
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        TextButton(
                            onClick = { 
                                tempSensitivity = swipeSensitivity
                                showSensitivityDialog = false 
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        TextButton(
                            onClick = { 
                                onSwipeSensitivityChange(tempSensitivity)
                                showSensitivityDialog = false 
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(MusicR.string.swipe_sensitivity),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
    
                        Text(
                            text = stringResource(MusicR.string.sensitivity_percentage, (tempSensitivity * 100).roundToInt()),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
    
                        Slider(
                            value = tempSensitivity,
                            onValueChange = { tempSensitivity = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            PreferenceEntry(
                title = { Text(stringResource(MusicR.string.swipe_sensitivity)) },
                description = stringResource(MusicR.string.sensitivity_percentage, (swipeSensitivity * 100).roundToInt()),
                icon = { Icon(painterResource(CoreR.drawable.tune), null) },
                onClick = { showSensitivityDialog = true }
            )
        }

        PreferenceGroupTitle(
            title = stringResource(MusicR.string.lyrics),
        )

        SwitchPreference(
            title = { Text("Lyrics V2 (Experimental)") },
            description = "Use the new fluid word-synced lyrics engine",
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            checked = useLyricsV2,
            onCheckedChange = onUseLyricsV2Change,
        )

        EnumListPreference(
            title = { Text(stringResource(MusicR.string.lyrics_text_position)) },
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            selectedValue = lyricsPosition,
            onValueSelected = onLyricsPositionChange,
            valueText = {
                when (it) {
                    LyricsPosition.LEFT -> stringResource(MusicR.string.left)
                    LyricsPosition.CENTER -> stringResource(MusicR.string.center)
                    LyricsPosition.RIGHT -> stringResource(MusicR.string.right)
                }
            },
        )

        EnumListPreference(
          title = { Text(stringResource(MusicR.string.lyrics_animation_style)) },
          icon = { Icon(painterResource(CoreR.drawable.animation), null) },
          selectedValue = lyricsAnimation,
          onValueSelected = onLyricsAnimationChange,
          valueText = {
              when (it) {
                  LyricsAnimationStyle.NONE -> stringResource(MusicR.string.none)
                  LyricsAnimationStyle.FADE -> stringResource(MusicR.string.fade)
                  LyricsAnimationStyle.GLOW -> stringResource(MusicR.string.glow)
                  LyricsAnimationStyle.SLIDE -> stringResource(MusicR.string.slide)
                  LyricsAnimationStyle.KARAOKE -> stringResource(MusicR.string.karaoke)
                  LyricsAnimationStyle.APPLE -> stringResource(MusicR.string.apple_music_style)
              }
          }
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.lyrics_click_change)) },
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            checked = lyricsClick,
            onCheckedChange = onLyricsClickChange,
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.lyrics_auto_scroll)) },
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            checked = lyricsScroll,
            onCheckedChange = onLyricsScrollChange,
        )

        var showLyricsTextSizeDialog by rememberSaveable { mutableStateOf(false) }
        
        if (showLyricsTextSizeDialog) {
            var tempTextSize by remember { mutableFloatStateOf(lyricsTextSize) }
            
            DefaultDialog(
                onDismiss = { 
                    tempTextSize = lyricsTextSize
                    showLyricsTextSizeDialog = false 
                },
                buttons = {
                    TextButton(
                        onClick = { 
                            tempTextSize = 24f
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(MusicR.string.reset))
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    TextButton(
                        onClick = { 
                            tempTextSize = lyricsTextSize
                            showLyricsTextSizeDialog = false 
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        onClick = { 
                            onLyricsTextSizeChange(tempTextSize)
                            showLyricsTextSizeDialog = false 
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(MusicR.string.lyrics_text_size),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "${tempTextSize.roundToInt()} sp",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Slider(
                        value = tempTextSize,
                        onValueChange = { tempTextSize = it },
                        valueRange = 16f..36f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        PreferenceEntry(
            title = { Text(stringResource(MusicR.string.lyrics_text_size)) },
            description = "${lyricsTextSize.roundToInt()} sp",
            icon = { Icon(painterResource(CoreR.drawable.text_fields), null) },
            onClick = { showLyricsTextSizeDialog = true }
        )
        
        var showLyricsLineSpacingDialog by rememberSaveable { mutableStateOf(false) }
        
        if (showLyricsLineSpacingDialog) {
            var tempLineSpacing by remember { mutableFloatStateOf(lyricsLineSpacing) }
            
            DefaultDialog(
                onDismiss = { 
                    tempLineSpacing = lyricsLineSpacing
                    showLyricsLineSpacingDialog = false 
                },
                buttons = {
                    TextButton(
                        onClick = { 
                            tempLineSpacing = 1.3f
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(MusicR.string.reset))
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    TextButton(
                        onClick = { 
                            tempLineSpacing = lyricsLineSpacing
                            showLyricsLineSpacingDialog = false 
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        onClick = { 
                            onLyricsLineSpacingChange(tempLineSpacing)
                            showLyricsLineSpacingDialog = false 
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(MusicR.string.lyrics_line_spacing),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "${String.format("%.1f", tempLineSpacing)}x",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Slider(
                        value = tempLineSpacing,
                        onValueChange = { tempLineSpacing = it },
                        valueRange = 1.0f..2.0f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        PreferenceEntry(
            title = { Text(stringResource(MusicR.string.lyrics_line_spacing)) },
            description = "${String.format("%.1f", lyricsLineSpacing)}x",
            icon = { Icon(painterResource(CoreR.drawable.text_fields), null) },
            onClick = { showLyricsLineSpacingDialog = true }
        )

        PreferenceGroupTitle(
            title = stringResource(MusicR.string.misc),
        )

        EnumListPreference(
            title = { Text(stringResource(MusicR.string.default_open_tab)) },
            icon = { Icon(painterResource(CoreR.drawable.nav_bar), null) },
            selectedValue = defaultOpenTab,
            onValueSelected = onDefaultOpenTabChange,
            valueText = {
                when (it) {
                    NavigationTab.HOME -> stringResource(MusicR.string.home)
                    NavigationTab.SEARCH -> stringResource(MusicR.string.search)
                    NavigationTab.LIBRARY -> stringResource(MusicR.string.filter_library)
                }
            },
        )

        ListPreference(
            title = { Text(stringResource(MusicR.string.default_lib_chips)) },
            icon = { Icon(painterResource(CoreR.drawable.tab), null) },
            selectedValue = defaultChip,
            values = listOf(
                LibraryFilter.LIBRARY, LibraryFilter.PLAYLISTS, LibraryFilter.SONGS,
                LibraryFilter.ALBUMS, LibraryFilter.ARTISTS
            ),
            valueText = {
                when (it) {
                    LibraryFilter.SONGS -> stringResource(MusicR.string.songs)
                    LibraryFilter.ARTISTS -> stringResource(MusicR.string.artists)
                    LibraryFilter.ALBUMS -> stringResource(MusicR.string.albums)
                    LibraryFilter.PLAYLISTS -> stringResource(MusicR.string.playlists)
                    LibraryFilter.LIBRARY -> stringResource(MusicR.string.filter_library)
                }
            },
            onValueSelected = onDefaultChipChange,
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.show_home_category_chips)) },
            description = stringResource(MusicR.string.show_home_category_chips_desc),
            icon = { Icon(painterResource(CoreR.drawable.home_outlined), null) },
            checked = showHomeCategoryChips,
            onCheckedChange = onShowHomeCategoryChipsChange,
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.show_tags_in_library)) },
            description = stringResource(MusicR.string.show_tags_in_library_desc),
            icon = { Icon(painterResource(CoreR.drawable.filter_alt), null) },
            checked = showTagsInLibrary,
            onCheckedChange = onShowTagsInLibraryChange,
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.swipe_song_to_add)) },
            icon = { Icon(painterResource(CoreR.drawable.swipe), null) },
            checked = swipeToSong,
            onCheckedChange = onSwipeToSongChange
        )


        EnumListPreference(
            title = { Text(stringResource(MusicR.string.grid_cell_size)) },
            icon = { Icon(painterResource(CoreR.drawable.grid_view), null) },
            selectedValue = gridItemSize,
            onValueSelected = onGridItemSizeChange,
            valueText = {
                when (it) {
                    GridItemSize.BIG -> stringResource(MusicR.string.big)
                    GridItemSize.SMALL -> stringResource(MusicR.string.small)
                }
            },
        )

        PreferenceGroupTitle(
            title = stringResource(MusicR.string.auto_playlists)
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.show_liked_playlist)) },
            icon = { Icon(painterResource(CoreR.drawable.favorite), null) },
            checked = showLikedPlaylist,
            onCheckedChange = onShowLikedPlaylistChange
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.show_downloaded_playlist)) },
            icon = { Icon(painterResource(CoreR.drawable.offline), null) },
            checked = showDownloadedPlaylist,
            onCheckedChange = onShowDownloadedPlaylistChange
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.show_top_playlist)) },
            icon = { Icon(painterResource(CoreR.drawable.trending_up), null) },
            checked = showTopPlaylist,
            onCheckedChange = onShowTopPlaylistChange
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.show_cached_playlist)) },
            icon = { Icon(painterResource(CoreR.drawable.cached), null) },
            checked = showCachedPlaylist,
            onCheckedChange = onShowCachedPlaylistChange
        )
    }

}

@Composable
private fun SliderStyleOptionCard(
    sliderStyle: SliderStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderValue by remember {
        mutableFloatStateOf(0.5f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        StyledPlaybackSlider(
            sliderStyle = sliderStyle,
            value = sliderValue,
            valueRange = 0f..1f,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {},
            activeColor = MaterialTheme.colorScheme.primary,
            isPlaying = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        Text(
            text = sliderStyleLabel(sliderStyle),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun sliderStyleLabel(sliderStyle: SliderStyle): String {
    return when (sliderStyle) {
        SliderStyle.Standard -> stringResource(MusicR.string.slider_style_standard)
        SliderStyle.Wavy -> stringResource(MusicR.string.slider_style_wavy)
        SliderStyle.Thick -> stringResource(MusicR.string.slider_style_thick)
        SliderStyle.Circular -> stringResource(MusicR.string.slider_style_circular)
        SliderStyle.Simple -> stringResource(MusicR.string.slider_style_simple)
    }
}

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    SEARCH,
    LIBRARY,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}

enum class PlayerTextAlignment {
    SIDED,
    CENTER,
}
