# LinerLayout 的坑

## layout weight 设置的坑

参考文档<https://www.cnblogs.com/w-y-f/p/4123056.html>中的内容。LinerLayout 中存在一个属性：

```
android:layout_weight="1"
```

这个属性的含义是给 View 分配剩余空间，而这个剩余空间是有正有负的。

例如代码：

```Xml
<？xml version="1.0" encoding="UTF-8"？>   
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"   
    android:layout_width="fill_parent"   
    android:layout_height="wrap_content"   
    android:orientation="horizontal" >   
    <TextView   
        android:background="＃ff0000"   
        android:layout_width="**"   
        android:layout_height="wrap_content"   
        android:text="1"   
        android:textColor="＠android:color/white"   
        android:layout_weight="1"/>   
    <TextView   
        android:background="＃cccccc"   
        android:layout_width="**"   
        android:layout_height="wrap_content"   
        android:text="2"   
        android:textColor="＠android:color/black"   
        android:layout_weight="2" />   
     <TextView   
        android:background="＃ddaacc"   
        android:layout_width="**"   
        android:layout_height="wrap_content"   
        android:text="3"   
        android:textColor="＠android:color/black"   
        android:layout_weight="3" />   
</LinearLayout> 
```

三个文本框的都是 **layout_width=“wrap_content** **”**时，会得到以下效果:

![img](http://images.51cto.com/files/uploadimg/20121231/1505301.jpg)

按照上面的理解，系统先给3个TextView分配他们的宽度值wrap_content（宽度足以包含他们的内容1,2,3即可），然后会把剩下来的屏幕空间按照1:2:3的比列分配给3个textview，所以就出现了上面的图像。

而当**layout_width=**“**fill_parent**”时，如果分别给三个TextView设置他们的Layout_weight为1、2、2的话，就会出现下面的效果：

![img](http://images.51cto.com/files/uploadimg/20121231/1505302.jpg)

系统先给3个textview分配他们所要的宽度fill_parent，也就是说每一都是填满他的父控件，这里就死屏幕的宽度

那么这时候的剩余空间=1个parent_width-3个parent_width=-2个parent_width (parent_width指的是屏幕宽度 )

那么第一个TextView的实际所占宽度应该=fill_parent的宽度,即parent_width + 他所占剩余空间的权重比列1/5 * 剩余空间大小（-2 parent_width）=3/5parent_width

同理第二个TextView的实际所占宽度=parent_width + 2/5*(-2parent_width)=1/5parent_width;

第三个TextView的实际所占宽度=parent_width + 2/5*(-2parent_width)=1/5parent_width；所以就是3:1:1的比列显示了。

这样你也就会明白为什么当你把三个Layout_weight设置为1、2、3的话,会出现下面的效果了：

![img](http://images.51cto.com/files/uploadimg/20121231/1505303.jpg)