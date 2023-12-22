# 从例子开始

接下来会从几个例子来接触 OpenGL。首先需要配一下环境，由于我们使用的书是现代 OpenGL 教程，因此我们也随他一起用 GLFW：

> GLFW是一个用C语言编写的库，专门针对OpenGL。GLFW为我们提供了渲染窗口所需的基本要素。它允许我们创建OpenGL上下文，定义窗口参数，并处理用户输入，这对我们的目的已经足够了。

```yaml
[dependencies]
glfw = "0.54.0"
anyhow = "1.0.76"
some-error = "0.5.0"
log = "0.4.20"
env_logger = "0.10.1"
```

# 创建一个 Window

创建图形应用程序的第一步是创建一个窗口。Let's do it!

```Rust
fn main() -> anyhow::Result<()> {
    init_log(LogLevel::TRACE);

    // 首先通过 glfwInit 可以初始化一个 glfw 对象，这是
    let mut glfw = glfw::init(glfw_error_callback).unwrap();
    // 配置 glfw 的版本为 3.3
    glfw.window_hint(WindowHint::ContextVersionMajor(3));
    glfw.window_hint(WindowHint::ContextVersionMinor(3));
    // 配置为 Core 模式，这也是我们学习的主要目标
    glfw.window_hint(WindowHint::OpenGlProfile(OpenGlProfileHint::Core));
}
```

> 需要注意，在Mac OSX上，如果是原版的 GLFW，需要在初始化代码中添加glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE)，才能正常工作。

`glfwInit`是创建了一个 GLFW 对象。

`glfwWindowHint`的第一个参数告诉我们想要配置的选项，我们可以从以GLFW_为前缀的可能选项的大型枚举中选择选项。第二个参数是一个整数，用于设置我们选项的值。可以在GLFW的窗口处理文档中找到所有可能选项及其相应的值的列表。

我们将这些配置抽象为一个全局库：

```
struct GlfwLocal {
    glfw: Glfw
}

impl GLFW_LOCAL {

    /// 创建一个本地 glfw 对象
    fn new() -> anyhow::Result<GlfwLocal> {
        let mut glfw = glfw::init(glfw_error_callback).unwrap();
        // 配置 glfw 的版本为 3.3
        glfw.window_hint(WindowHint::ContextVersionMajor(3));
        glfw.window_hint(WindowHint::ContextVersionMinor(3));
        // 配置为 Core 模式，这也是我们学习的主要目标
        glfw.window_hint(WindowHint::OpenGlProfile(OpenGlProfileHint::Core));

        Ok(GlfwLocal { glfw })
    }
}
```

然后准备创建一个窗口：

```rust
```

