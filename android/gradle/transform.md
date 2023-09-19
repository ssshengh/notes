# Transform 介绍及例子



由 Android 的编译过程可知，只要在生成 class 文件之后，dex 文件之前进行拦截，就可以拿到当前应用程序中所有的.class文件，再去借助ASM之类的库，就可以遍历这些.class文件中所有方法，再根据一定的条件找到需要的目标方法，最后进行修改并保存，就可以插入我们的埋点代码。

核心就是：Transform 是提供给我们接触到 .class 文件的工作，而实际上要修改 .class 文件或者生成新的 .class 文件需要 ASM 等方式。

# 基础概念

每个Transform其实都是一个gradle task，Android编译器中的TaskManager将每个Transform串连起来，第一个Transform接收来自javac编译的结果，以及已经拉取到在本地的第三方依赖（jar、aar），还有resource资源，注意，这里的resource并非android项目中的res资源，而是asset目录下的资源。 这些编译的中间产物，在Transform组成的链条上流动，每个Transform节点可以对class进行处理再传递给下一个Transform。我们常见的混淆，Desugar等逻辑，它们的实现如今都是封装在一个个Transform中，而我们自定义的Transform，会插入到这个Transform链条的最前面。

最终，我们定义的Transform会被转化成一个个TransformTask，在Gradle编译时调用。

Transform 可以视为一个通道，.class 文件进入之后，出来 .class 文件，然后进入下一个 transform 通道，直到最后的 transform，最后编译器拿到这些所有的 .class 文件来编译得到 .dex 文件。因此，对于 Transform 抽象来说，最关键的就是两个因素：

1. 进入：TransformInput
2. 离开：TransformOutputProvider

TransformInput是指输入文件的一个抽象，包括：

- DirectoryInput集合
   是指以源码的方式参与项目编译的所有目录结构及其目录下的源码文件
- JarInput集合
   是指以jar包方式参与项目编译的所有本地jar包和远程jar包（此处的jar包包括aar）

TransformOutputProvider 则是 Transform的输出，通过它可以获取到输出路径等信息

# Transform 抽象

在源码中 Transform 大概是这样：

```java
public abstract class Transform {
    public Transform() {
    }

    // Transform名称
    public abstract String getName();

    public abstract Set<ContentType> getInputTypes();

    public Set<ContentType> getOutputTypes() {
        return this.getInputTypes();
    }

    public abstract Set<? super Scope> getScopes();


    public abstract boolean isIncremental();

    /** @deprecated */
    @Deprecated
    public void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs, TransformOutputProvider outputProvider, boolean isIncremental) throws IOException, TransformException, InterruptedException {
    }

    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        this.transform(transformInvocation.getContext(), transformInvocation.getInputs(), transformInvocation.getReferencedInputs(), transformInvocation.getOutputProvider(), transformInvocation.isIncremental());
    }

    public boolean isCacheable() {
        return false;
    }
    
    ...
}
```

## Transform#getName()

该方法获得Transform名称，例如下图的红框中部分：

![img](https://upload-images.jianshu.io/upload_images/9513946-22668a0486d18efc.png?imageMogr2/auto-orient/strip|imageView2/2/w/1200/format/webp)

在gradle plugin的源码中有一个叫TransformManager的类，这个类管理着所有的Transform的子类，里面有一个方法叫getTaskNamePrefix，在这个方法中就是获得Task的前缀，以transform开头，之后拼接ContentType，这个ContentType代表着这个Transform的输入文件的类型，类型主要有两种，一种是Classes，另一种是Resources，ContentType之间使用And连接，拼接完成后加上With，之后紧跟的就是这个Transform的Name，name在getName()方法中重写返回即可。TransformManager#getTaskNamePrefix()代码如下：

```java
static String getTaskNamePrefix(Transform transform) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("transform");
        sb.append((String)transform.getInputTypes().stream().map((inputType) -> {
            return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, inputType.name());
        }).sorted().collect(Collectors.joining("And")));
        sb.append("With");
        StringHelper.appendCapitalized(sb, transform.getName());
        sb.append("For");
        return sb.toString();
    }
```

## Transform#getInputTypes()

需要处理的数据类型，有两种枚举类型

- CLASSES
   代表处理的 java 的 class 文件，返回TransformManager.CONTENT_CLASS
- RESOURCES
   代表要处理 java 的资源，返回TransformManager.CONTENT_RESOURCES

但是一般我们返回的都是一个`Set`，在`TransformManager`中 Android 提供了很多类型，例如：

```java
public class TransformManager extends FilterableStreamCollection {

    private static final boolean DEBUG = true;

    private static final String FD_TRANSFORMS = "transforms";

    public static final Set<ScopeType> EMPTY_SCOPES = ImmutableSet.of();
		
  	// 一系列 CLASS 的结合，我们用得最多的
    public static final Set<ContentType> CONTENT_CLASS = ImmutableSet.of(CLASSES);
    public static final Set<ContentType> CONTENT_JARS = ImmutableSet.of(CLASSES, RESOURCES);
    public static final Set<ContentType> CONTENT_RESOURCES = ImmutableSet.of(RESOURCES);
    public static final Set<ContentType> CONTENT_NATIVE_LIBS =
            ImmutableSet.of(NATIVE_LIBS);
    public static final Set<ContentType> CONTENT_DEX = ImmutableSet.of(ExtendedContentType.DEX);
    public static final Set<ContentType> CONTENT_DEX_WITH_RESOURCES =
            ImmutableSet.of(ExtendedContentType.DEX, RESOURCES);
    public static final Set<ScopeType> PROJECT_ONLY = ImmutableSet.of(Scope.PROJECT);
    public static final Set<ScopeType> SCOPE_FULL_PROJECT =
            ImmutableSet.of(Scope.PROJECT, Scope.SUB_PROJECTS, Scope.EXTERNAL_LIBRARIES);
    public static final Set<ScopeType> SCOPE_FULL_WITH_FEATURES =
            new ImmutableSet.Builder<ScopeType>()
                    .addAll(SCOPE_FULL_PROJECT)
                    .add(InternalScope.FEATURES)
                    .build();
    public static final Set<ScopeType> SCOPE_FEATURES = ImmutableSet.of(InternalScope.FEATURES);
    public static final Set<ScopeType> SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS =
            ImmutableSet.of(Scope.PROJECT, InternalScope.LOCAL_DEPS);
    public static final Set<ScopeType> SCOPE_FULL_PROJECT_WITH_LOCAL_JARS =
            new ImmutableSet.Builder<ScopeType>()
                    .addAll(SCOPE_FULL_PROJECT)
                    .add(InternalScope.LOCAL_DEPS)
                    .build();
		// ...
}
```

## Transform#getScopes()

指 Transform 要操作内容的范围，官方文档 Scope 有 7 种类型：

1. EXTERNAL_LIBRARIES ：              只有外部库
2. PROJECT ：                         只有项目内容
3. PROJECT_LOCAL_DEPS ：              只有项目的本地依赖(本地jar)(未来会被抛弃)
4. PROVIDED_ONLY ：                   只提供本地或远程依赖项
5. SUB_PROJECTS ：                    只有子项目
6. SUB_PROJECTS_LOCAL_DEPS：          只有子项目的本地依赖项(本地jar)(未来会被抛弃)
7. TESTED_CODE ：                     由当前变量(包括依赖项)测试的代码

如果要处理所有的class字节码，返回TransformManager.SCOPE_FULL_PROJECT

## getReferencedScopes()

> 这个方法比较有意思：
>
> Returns the referenced scope(s) for the Transform. These scopes are **not consumed by the Transform**. They are provided as inputs, but are still available as inputs for other Transforms to consume.
>
> 其实意思就是可以作为 inputs，但是依旧会被其他的 transform 给处理。一般来说用于看内部的内容。
>
> The default implementation returns an empty Set.

返回的内容即指定该Transform的查看input文件的作用域:

```
    @Override
    public Set<? super QualifiedContent.Scope> getReferencedScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }
    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }
```

getReferencedScopes()区别于getScopes()，复写transform()并不会覆盖Android原来的.class文件转换成dex文件的过程。该方法主要用来该自定义的Transform并不想处理任何input文件的内容，仅仅只是想查看input文件的内容的作用域范围。 如下源码有一段解释：
![image-20230721101052957](/Users/bytedance/Library/Application Support/typora-user-images/image-20230721101052957.png)

所以要实现只查看input文件的内容，设置getReferencedScopes()的作用域范围，同时需要将getScopes()返回一个空集合，如上代码所示。这样在transform()可以查看该.class文件转换成dex文件的过程，不改变原来Android的打包apk的逻辑。

## Transform#isIncremental()

增量编译开关

当我们开启增量编译的时候，相当input包含了changed/removed/added三种状态，实际上还有notchanged。需要做的操作如下：

- NOTCHANGED: 当前文件不需处理，甚至复制操作都不用；
- ADDED、CHANGED: 正常处理，输出给下一个任务；
- REMOVED: 移除outputProvider获取路径对应的文件。

## Transform#transform()

```java
// Android Gradle已经将input和output打包成一个TransformInvocation对象
public void transform(@NonNull TransformInvocation transformInvocation)
        throws TransformException, InterruptedException, IOException {
    // Just delegate to old method, for code that uses the old API.
    //noinspection deprecation
    transform(transformInvocation.getContext(), transformInvocation.getInputs(),
            transformInvocation.getReferencedInputs(),
            transformInvocation.getOutputProvider(),
            transformInvocation.isIncremental());
}
```

注意点

- 如果拿取了getInputs()的输入进行消费，则transform后必须再输出给下一级
- 如果拿取了getReferencedInputs()的输入，则不应该被transform
- 是否增量编译要以transformInvocation.isIncremental()为准

具体能够拿到的输入内容可以看以下 API：

```java
public interface TransformInvocation {
    /**
     * 返回正在处理哪个Context，该Context包含项目名称、路径等信息
     */
    Context getContext();
    /**
     * 返回通过getScope()设置的所有的input
     */
    Collection<TransformInput> getInputs();
    /**
     * 返回通过getReferencedScopes()设置的referenced-only 的input
     */
    Collection<TransformInput> getReferencedInputs();
    /**
     * 返回secondaryInputs
     */
    @NonNull Collection<SecondaryInput> getSecondaryInputs();
    /**
     * 可以设置output的相关内容
     */
    TransformOutputProvider getOutputProvider();
    /**
     * 返回是否是增量编译
     */
    boolean isIncremental();
}
```

## Transform#isCacheable()

如果我们的transform需要被缓存，则为true，它被TransformTask所用到

