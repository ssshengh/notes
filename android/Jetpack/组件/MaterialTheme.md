# MaterialTheme

## 简介

`MaterialTheme` 在Android的Jetpack Compose框架中是用来定义整个应用的颜色、排版、形状等设计元素。你可以通过它来实现Material Design的主题。以下是如何使用`MaterialTheme`的基本步骤：

1. 定义你的颜色方案：

```kotlin
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors

private val DarkColorPalette = darkColors(
    primary = Color(0xFFBB86FC),
    primaryVariant = Color(0xFF3700B3),
    secondary = Color(0xFF03DAC5),
    // Other colors for your theme
)

private val LightColorPalette = lightColors(
    primary = Color(0xFF6200EE),
    primaryVariant = Color(0xFF3700B3),
    secondary = Color(0xFF03DAC5),
    // Other colors for your theme
)
```

2. 定义排版和形状方案：

```kotlin
import androidx.compose.material.Typography
import androidx.compose.material.Shapes

private val MyTypography = Typography(
    // Define your font family and sizes
)

private val MyShapes = Shapes(
    // Define your shapes
)
```

3. 在 App 的最顶层封装`MaterialTheme`来应用你的主题：

```kotlin
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun MyApp() {
    val isDarkTheme = true // You might want to get this from a user setting or system setting

    MaterialTheme(
        colors = if (isDarkTheme) DarkColorPalette else LightColorPalette,
        typography = MyTypography,
        shapes = MyShapes
    ) {
        // Your app's UI components go here.
    }
}
```

3. 在你的其他可组合函数（Composables）中使用主题属性：

```kotlin
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MyComposable() {
    MaterialTheme {
        Text(
            text = "Hello, Material Theme!",
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.primary
        )
    }
}
```

注意，在这个例子里，`MyApp()` 是最外层的Composable，并且包了整个应用的UI。任何在`MaterialTheme`里面的Composable都会自动继承主题属性。请确保你的项目已经导入了Jetpack Compose的相关库，并且你的IDE（例如Android Studio）已经更新到支持Compose的版本。

> 参考文档：https://juejin.cn/post/7064410835422019615

## 基于官方例子 JetNews 进行介绍

官方在例子中给到了一个定义为全局的 Theme：

```kotlin
// 所有的组件都用了这个 Theme
@Composable
fun JetnewsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
  	// 颜色定义
    val colorScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) DarkColors else LightColors
        }
		
  	// 通过 MaterialTheme 来管理内容，内容就是 Composable 方法
    MaterialTheme(
        colorScheme = colorScheme,
      	// 形状
        shapes = JetnewsShapes,
      	// 排版
        typography = JetnewsTypography,
        content = content
    )
}
```

我们分别来说。

### Color

在 Theme 的 Color 中分为两种：

1. 静态配置颜色
2. 动态颜色

[动态颜色](https://m3.material.io/styles/color/dynamic-color/overview)是 Material You 的关键部分，在此过程中，算法会从用户的壁纸中派生自定义颜色，以将其应用到其应用和系统界面。此调色板可用作生成浅色和深色配色方案的起点。动态颜色需要安卓 12 以上的支持：

```kotlin
  	// 颜色定义
val colorScheme =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
      	// 动态颜色
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
      	// 静态配置颜色
        if (darkTheme) DarkColors else LightColors
    }
```

而静态配置的颜色就是上面给到的两个变量：

```kotlin
val DarkColors = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
)

val md_theme_dark_primary = Color(0xFFFFB3B4)
val md_theme_dark_onPrimary = Color(0xFF680016)
val md_theme_dark_primaryContainer = Color(0xFF920023)
val md_theme_dark_onPrimaryContainer = Color(0xFFFFDAD9)
// ...
```

Material 提供了 data class 来定义各种场景下的颜色