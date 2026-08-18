package com.tilpad.ime

/**
 * 拼音引擎 — 基础拼音转汉字。
 *
 * 包含常用汉字字典，支持单字和常见词语匹配。
 * 算法：
 * 1. 用户输入拼音字母（如 "nihao"）
 * 2. 先尝试整体匹配词语（如 "nihao" → "你好"）
 * 3. 再尝试按音节拆分匹配单字（如 "ni" → "你", "hao" → "好"）
 * 4. 返回候选列表供用户选择
 *
 * 字典规模：约 400 常用单字 + 200 常用词语
 * 不追求完美，覆盖日常交流基本需求。
 */
object PinyinEngine {

    /** 单字字典：拼音 → 汉字列表（按频率排序） */
    private val charMap: Map<String, List<String>> = mapOf(
        "a" to listOf("啊", "阿", "呵", "嗄"),
        "ai" to listOf("爱", "艾", "哎", "唉", "埃", "挨", "癌", "矮", "蔼", "隘"),
        "an" to listOf("安", "按", "案", "暗", "岸", "俺", "氨", "鞍", "黯"),
        "ang" to listOf("昂", "肮", "盎"),
        "ao" to listOf("奥", "傲", "澳", "凹", "袄", "熬", "翱", "拗"),
        "ba" to listOf("把", "吧", "爸", "八", "巴", "罢", "拔", "靶", "霸", "疤"),
        "bai" to listOf("白", "百", "拜", "摆", "败", "柏", "伯", "掰"),
        "ban" to listOf("办", "半", "班", "板", "般", "版", "搬", "伴", "扮", "绊", "瓣"),
        "bang" to listOf("帮", "棒", "榜", "膀", "绑", "磅", "蚌"),
        "bao" to listOf("报", "包", "保", "宝", "抱", "暴", "薄", "饱", "爆", "堡", "鲍"),
        "bei" to listOf("被", "北", "背", "倍", "备", "杯", "悲", "辈", "碑", "贝", "惫"),
        "ben" to listOf("本", "奔", "笨", "苯", "锛"),
        "beng" to listOf("崩", "绷", "蹦", "泵"),
        "bi" to listOf("比", "必", "毕", "笔", "闭", "币", "壁", "避", "鼻", "彼", "逼", "弊", "碧", "臂"),
        "bian" to listOf("边", "变", "便", "编", "遍", "辨", "辩", "扁", "贬", "鞭"),
        "biao" to listOf("表", "标", "彪", "膘", "飙"),
        "bie" to listOf("别", "憋", "瘪", "鳖"),
        "bin" to listOf("宾", "滨", "缤", "槟", "濒"),
        "bing" to listOf("并", "病", "兵", "冰", "丙", "秉", "饼", "炳"),
        "bo" to listOf("不", "波", "播", "博", "拨", "伯", "驳", "勃", "搏", "薄", "泊"),
        "bu" to listOf("不", "部", "步", "布", "补", "簿", "埠", "哺"),
        "ca" to listOf("擦", "嚓"),
        "cai" to listOf("才", "采", "菜", "财", "材", "裁", "踩", "彩", "蔡"),
        "can" to listOf("参", "残", "餐", "惨", "灿", "仓", "蚕"),
        "cang" to listOf("仓", "藏", "沧", "苍", "舱"),
        "cao" to listOf("草", "操", "曹", "槽", "糙"),
        "ce" to listOf("侧", "策", "册", "测", "厕"),
        "ceng" to listOf("层", "曾", "蹭"),
        "cha" to listOf("查", "差", "插", "茶", "察", "叉", "岔", "刹", "碴"),
        "chai" to listOf("拆", "柴", "差", "豺"),
        "chan" to listOf("产", "缠", "颤", "蝉", "馋", "掺", "搀", "阐", "铲"),
        "chang" to listOf("长", "场", "常", "厂", "唱", "尝", "偿", "畅", "倡", "肠", "敞", "怅"),
        "chao" to listOf("超", "抄", "朝", "潮", "吵", "炒", "巢", "钞"),
        "che" to listOf("车", "彻", "撤", "扯", "澈"),
        "chen" to listOf("称", "陈", "晨", "沉", "趁", "臣", "辰", "尘", "衬", "忱"),
        "cheng" to listOf("成", "城", "程", "称", "承", "乘", "惩", "橙", "逞", "秤"),
        "chi" to listOf("吃", "持", "迟", "池", "尺", "斥", "齿", "翅", "痴", "耻", "驰", "弛"),
        "chong" to listOf("冲", "充", "虫", "崇", "宠", "重", "冲"),
        "chou" to listOf("抽", "愁", "丑", "臭", "仇", "绸", "酬", "畴", "踌"),
        "chu" to listOf("出", "处", "初", "除", "厨", "础", "储", "触", "楚", "畜", "橱", "触"),
        "chuan" to listOf("传", "穿", "船", "川", "喘", "串", "椽"),
        "chuang" to listOf("窗", "床", "创", "闯", "疮", "幢"),
        "chui" to listOf("吹", "垂", "锤", "炊", "捶"),
        "chun" to listOf("春", "纯", "蠢", "唇", "醇"),
        "chuo" to listOf("戳", "绰", "啜"),
        "ci" to listOf("次", "此", "词", "刺", "慈", "瓷", "辞", "磁", "雌", "赐"),
        "cong" to listOf("从", "聪", "丛", "匆", "葱", "琮"),
        "cou" to listOf("凑", "揍"),
        "cu" to listOf("粗", "促", "醋", "簇", "蹙"),
        "cuan" to listOf("窜", "篡", "蹿", "攒"),
        "cui" to listOf("催", "摧", "脆", "翠", "崔", "淬", "粹"),
        "cun" to listOf("村", "存", "寸", "蹲"),
        "cuo" to listOf("措", "搓", "错", "挫", "撮", "蹉"),
        "da" to listOf("大", "打", "达", "搭", "答", "瘩", "怛", "妲"),
        "dai" to listOf("代", "带", "待", "戴", "袋", "逮", "怠", "贷", "大", "岱"),
        "dan" to listOf("但", "单", "担", "蛋", "弹", "胆", "旦", "淡", "诞", "丹", "惮"),
        "dang" to listOf("当", "党", "挡", "档", "荡", "档"),
        "dao" to listOf("到", "道", "倒", "岛", "导", "刀", "悼", "盗", "稻", "蹈"),
        "de" to listOf("的", "得", "德", "地", "登"),
        "dei" to listOf("得"),
        "deng" to listOf("等", "灯", "登", "邓", "瞪", "凳", "蹬"),
        "di" to listOf("第", "地", "低", "底", "弟", "敌", "帝", "递", "滴", "堤", "抵", "迪", "笛", "狄"),
        "dian" to listOf("点", "电", "店", "典", "颠", "垫", "淀", "殿", "惦", "滇", "癫"),
        "diao" to listOf("掉", "调", "钓", "刁", "雕", "吊", "鸟", "叼"),
        "die" to listOf("跌", "叠", "蝶", "谍", "迭", "爹", "碟"),
        "ding" to listOf("定", "顶", "丁", "钉", "订", "盯", "鼎", "叮", "仃"),
        "diu" to listOf("丢"),
        "dong" to listOf("动", "东", "洞", "冬", "懂", "董", "冻", "栋", "咚"),
        "dou" to listOf("都", "斗", "豆", "抖", "陡", "逗", "兜"),
        "du" to listOf("度", "读", "独", "督", "毒", "渡", "堵", "赌", "睹", "杜", "肚", "镀"),
        "duan" to listOf("段", "短", "断", "端", "锻", "缎"),
        "dui" to listOf("对", "队", "堆", "兑", "憝"),
        "dun" to listOf("顿", "吨", "蹲", "盾", "钝", "炖", "墩", "遁"),
        "duo" to listOf("多", "夺", "朵", "躲", "堕", "舵", "剁", "跺", "哆"),
        "e" to listOf("饿", "额", "恶", "鹅", "俄", "鄂", "蛾", "厄", "遏"),
        "en" to listOf("恩", "嗯", "摁"),
        "er" to listOf("而", "二", "耳", "儿", "尔", "饵", "贰", "迩"),
        "fa" to listOf("发", "法", "罚", "乏", "伐", "阀", "筏", "珐"),
        "fan" to listOf("反", "翻", "凡", "烦", "繁", "返", "范", "犯", "饭", "泛", "藩", "梵"),
        "fang" to listOf("方", "放", "房", "防", "妨", "仿", "访", "纺", "舫"),
        "fei" to listOf("非", "飞", "费", "废", "肥", "匪", "沸", "啡", "菲", "蜚", "扉"),
        "fen" to listOf("分", "粉", "份", "纷", "奋", "愤", "坟", "焚", "酚"),
        "feng" to listOf("风", "封", "丰", "峰", "锋", "蜂", "风", "逢", "缝", "讽", "凤", "奉"),
        "fo" to listOf("佛"),
        "fou" to listOf("否", "缶"),
        "fu" to listOf("不", "服", "府", "父", "附", "富", "复", "福", "负", "副", "扶", "浮", "符", "腐", "肤", "覆", "弗", "甫", "抚", "赋", "腹"),
        "ga" to listOf("噶", "嘎", "尬"),
        "gai" to listOf("该", "改", "概", "盖", "溉", "丐", "钙", "戤"),
        "gan" to listOf("干", "敢", "感", "赶", "秆", "肝", "甘", "柑", "尴", "泔"),
        "gang" to listOf("刚", "岗", "港", "钢", "纲", "缸", "杠", "罡"),
        "gao" to listOf("高", "告", "搞", "稿", "糕", "膏", "羔", "睾", "篙"),
        "ge" to listOf("个", "歌", "哥", "革", "格", "隔", "割", "阁", "搁", "戈", "葛", "阁", "铬"),
        "gei" to listOf("给"),
        "gen" to listOf("根", "跟", "艮", "哏"),
        "geng" to listOf("更", "耕", "梗", "耿", "庚", "羹"),
        "gong" to listOf("工", "公", "共", "功", "攻", "供", "宫", "弓", "巩", "贡", "汞", "拱"),
        "gou" to listOf("够", "狗", "购", "勾", "沟", "钩", "苟", "垢", "构"),
        "gu" to listOf("古", "故", "顾", "骨", "股", "谷", "鼓", "姑", "孤", "雇", "辜", "估", "枯"),
        "gua" to listOf("挂", "瓜", "寡", "刮", "褂", "卦"),
        "guai" to listOf("怪", "拐", "乖"),
        "guan" to listOf("关", "管", "观", "官", "馆", "惯", "灌", "贯", "冠", "罐", "莞"),
        "guang" to listOf("光", "广", "逛"),
        "gui" to listOf("贵", "规", "归", "鬼", "桂", "柜", "跪", "轨", "瑰", "龟", "诡"),
        "gun" to listOf("滚", "棍", "衮", "磙"),
        "guo" to listOf("国", "过", "果", "锅", "裹", "郭", "涡", "帼"),
        "ha" to listOf("哈", "蛤", "铪"),
        "hai" to listOf("还", "海", "害", "孩", "骇", "嗨"),
        "han" to listOf("汉", "含", "寒", "喊", "汗", "韩", "涵", "旱", "翰", "罕", "捍"),
        "hang" to listOf("航", "行", "杭", "夯"),
        "hao" to listOf("好", "号", "豪", "毫", "浩", "耗", "郝", "嚎"),
        "he" to listOf("和", "河", "合", "何", "核", "贺", "喝", "荷", "褐", "鹤", "呵", "赫"),
        "hei" to listOf("黑", "嘿"),
        "hen" to listOf("很", "恨", "痕", "狠"),
        "heng" to listOf("横", "恒", "哼", "珩"),
        "hong" to listOf("红", "洪", "宏", "轰", "弘", "虹", "鸿"),
        "hou" to listOf("后", "候", "厚", "侯", "喉", "猴", "吼", "堠"),
        "hu" to listOf("胡", "虎", "湖", "互", "户", "护", "呼", "忽", "糊", "弧", "壶", "瑚", "蝴", "乎"),
        "hua" to listOf("化", "话", "花", "华", "画", "划", "滑", "哗", "豁", "桦"),
        "huai" to listOf("坏", "怀", "槐", "淮", "徊"),
        "huan" to listOf("换", "还", "环", "缓", "幻", "欢", "焕", "唤", "患", "宦", "浣"),
        "huang" to listOf("黄", "荒", "慌", "皇", "煌", "晃", "谎", "簧", "凰", "惶"),
        "hui" to listOf("会", "回", "灰", "汇", "挥", "辉", "惠", "悔", "毁", "慧", "绘", "贿", "晖", "晦"),
        "hun" to listOf("混", "婚", "魂", "昏", "浑", "荤"),
        "huo" to listOf("活", "火", "或", "获", "货", "祸", "霍", "豁", "惑"),
        "ji" to listOf("机", "几", "及", "级", "即", "积", "集", "基", "际", "计", "记", "纪", "技", "击", "挤", "济", "继", "寄", "吉", "急", "疾", "棘", "辑", "籍", "妓", "忌"),
        "jia" to listOf("家", "加", "假", "价", "架", "佳", "甲", "嘉", "嫁", "驾", "挟", "稼"),
        "jian" to listOf("见", "间", "建", "简", "件", "坚", "键", "检", "减", "剑", "简", "肩", "艰", "荐", "践", "鉴"),
        "jiang" to listOf("将", "江", "讲", "姜", "蒋", "匠", "奖", "桨", "僵", "疆", "缰"),
        "jiao" to listOf("叫", "教", "交", "较", "脚", "角", "搅", "缴", "骄", "娇", "蕉", "酵", "轿"),
        "jie" to listOf("的", "结", "接", "街", "节", "解", "界", "介", "届", "借", "戒", "杰", "捷", "裁", "竭", "睫"),
        "jin" to listOf("进", "金", "今", "近", "紧", "斤", "禁", "尽", "劲", "浸", "晋", "仅", "谨", "襟"),
        "jing" to listOf("经", "精", "京", "景", "静", "境", "警", "井", "竞", "净", "敬", "惊", "镜", "径", "痉", "荆"),
        "jiong" to listOf("窘", "炯", "迥"),
        "jiu" to listOf("就", "九", "久", "旧", "救", "酒", "纠", "舅", "咎", "韭", "疚", "厩"),
        "ju" to listOf("局", "据", "句", "举", "具", "聚", "拒", "距", "剧", "巨", "俱", "菊", "矩", "锯", "沮"),
        "juan" to listOf("卷", "圈", "倦", "捐", "涓", "眷", "绢", "镌"),
        "jue" to listOf("决", "觉", "绝", "角", "嚼", "爵", "掘", "诀", "崛", "厥"),
        "jun" to listOf("军", "均", "君", "菌", "俊", "峻", "骏", "竣"),
        "ka" to listOf("卡", "咖", "喀", "咯"),
        "kai" to listOf("开", "凯", "慨", "揩", "楷", "洽"),
        "kan" to listOf("看", "刊", "堪", "勘", "坎", "侃"),
        "kang" to listOf("抗", "康", "慷", "扛", "亢", "炕"),
        "kao" to listOf("考", "靠", "拷", "烤", "铐"),
        "ke" to listOf("可", "课", "克", "客", "科", "颗", "刻", "渴", "壳", "咳", "磕", "苛", "柯"),
        "ken" to listOf("肯", "啃", "恳", "垦"),
        "keng" to listOf("坑", "铿"),
        "kong" to listOf("空", "恐", "控", "孔", "倥"),
        "kou" to listOf("口", "扣", "寇", "抠"),
        "ku" to listOf("苦", "哭", "裤", "酷", "枯", "窟", "骷"),
        "kua" to listOf("跨", "夸", "垮", "挎", "胯"),
        "kuai" to listOf("快", "块", "筷", "会", "侩"),
        "kuan" to listOf("宽", "款"),
        "kuang" to listOf("况", "狂", "矿", "筐", "框", "旷", "眶", "匡"),
        "kui" to listOf("亏", "愧", "馈", "葵", "魁", "窥", "奎"),
        "kun" to listOf("困", "昆", "捆", "坤", "琨"),
        "kuo" to listOf("扩", "阔", "括", "廓"),
        "la" to listOf("拉", "啦", "落", "腊", "辣", "蜡", "喇", "垃"),
        "lai" to listOf("来", "赖", "莱", "籁"),
        "lan" to listOf("蓝", "兰", "烂", "栏", "懒", "拦", "缆", "览", "滥", "篮", "澜"),
        "lang" to listOf("浪", "郎", "狼", "廊", "朗", "蒗"),
        "lao" to listOf("老", "劳", "落", "牢", "捞", "烙", "佬", "唠", "涝"),
        "le" to listOf("了", "乐", "勒", "肋"),
        "lei" to listOf("类", "累", "雷", "泪", "垒", "擂", "蕾", "磊"),
        "leng" to listOf("冷", "愣", "棱", "玲"),
        "li" to listOf("里", "理", "力", "立", "利", "例", "离", "历", "礼", "李", "丽", "里", "哩", "莉", "荔", "璃", "吏", "砾"),
        "lia" to listOf("俩"),
        "lian" to listOf("连", "联", "练", "炼", "脸", "莲", "怜", "廉", "恋", "链", "帘", "镰", "敛"),
        "liang" to listOf("两", "量", "凉", "梁", "良", "亮", "辆", "谅", "晾", "粮", "踉"),
        "liao" to listOf("了", "料", "聊", "辽", "疗", "燎", "僚", "寥", "嘹"),
        "lie" to listOf("列", "烈", "劣", "猎", "裂", "咧", "冽", "洌"),
        "lin" to listOf("林", "临", "邻", "磷", "淋", "琳", "麟", "吝", "拎", "凛"),
        "ling" to listOf("令", "灵", "领", "另", "零", "龄", "岭", "陵", "玲", "铃", "棱", "翎", "伶"),
        "liu" to listOf("六", "留", "流", "刘", "柳", "硫", "溜", "浏", "榴", "琉"),
        "long" to listOf("龙", "隆", "笼", "笼", "聋", "拢", "陇", "胧", "珑"),
        "lou" to listOf("楼", "搂", "篓", "漏", "陋", "娄", "露"),
        "lu" to listOf("路", "陆", "录", "鲁", "卢", "炉", "芦", "虏", "鹿", "露", "禄", "噜", "鹭"),
        "lv" to listOf("绿", "律", "率", "旅", "虑", "滤", "氯", "履"),
        "luan" to listOf("乱", "卵", "峦", "挛", "滦"),
        "lue" to listOf("略", "掠"),
        "lun" to listOf("论", "轮", "伦", "沦", "纶", "仑"),
        "luo" to listOf("落", "罗", "骆", "洛", "螺", "萝", "锣", "箩", "骡", "裸", "络"),
        "ma" to listOf("妈", "马", "吗", "麻", "骂", "嘛", "码", "玛", "蚂"),
        "mai" to listOf("买", "卖", "迈", "麦", "脉", "埋"),
        "man" to listOf("满", "慢", "曼", "漫", "蛮", "馒", "瞒", "幔", "蔓"),
        "mang" to listOf("忙", "茫", "盲", "芒", "莽"),
        "mao" to listOf("毛", "冒", "帽", "猫", "贸", "茅", "矛", "茂", "髦", "锚"),
        "me" to listOf("么", "麽"),
        "mei" to listOf("没", "每", "美", "妹", "梅", "媒", "煤", "眉", "霉", "枚", "玫", "媚", "湄"),
        "men" to listOf("们", "门", "闷", "焖", "懑"),
        "meng" to listOf("梦", "猛", "蒙", "盟", "孟", "朦", "锰", "蜢", "蟒"),
        "mi" to listOf("米", "密", "迷", "蜜", "秘", "眯", "弥", "谜", "靡", "糜", "咪"),
        "mian" to listOf("面", "棉", "免", "勉", "绵", "缅", "冕", "娩"),
        "miao" to listOf("秒", "苗", "描", "妙", "庙", "瞄", "淼", "缈"),
        "mie" to listOf("灭", "蔑", "咩"),
        "min" to listOf("民", "敏", "明", "闽", "悯", "抿"),
        "ming" to listOf("明", "名", "命", "鸣", "铭", "冥", "螟", "溟"),
        "miu" to listOf("谬"),
        "mo" to listOf("莫", "磨", "模", "末", "摸", "膜", "摩", "魔", "沫", "陌", "茉", "墨", "默", "漠"),
        "mou" to listOf("某", "谋", "牟", "眸"),
        "mu" to listOf("木", "目", "母", "幕", "墓", "幕", "暮", "慕", "牟", "牧", "穆", "姆", "拇"),
        "na" to listOf("那", "拿", "哪", "钠", "呐", "娜", "捺"),
        "n" to listOf("那", "哪", "年", "您", "女", "弄", "能", "呢", "难", "内", "南", "脑", "鸟", "念", "娘", "农", "奴", "暖", "挪"),
        "nai" to listOf("乃", "奶", "耐", "奈", "奶", "艿"),
        "nan" to listOf("南", "男", "难", "楠", "喃", "腩"),
        "nang" to listOf("囊", "囔"),
        "nao" to listOf("脑", "闹", "挠", "恼", "淖", "瑙"),
        "ne" to listOf("呢", "讷"),
        "nei" to listOf("内", "馁"),
        "nen" to listOf("嫩", "恁"),
        "neng" to listOf("能"),
        "ni" to listOf("你", "尼", "拟", "妮", "泥", "倪", "霓", "腻", "溺", "逆", "匿", "腻"),
        "nian" to listOf("年", "念", "粘", "碾", "捻", "酿", "蔫"),
        "niang" to listOf("娘", "酿"),
        "niao" to listOf("鸟", "尿", "溺"),
        "nie" to listOf("捏", "聂", "涅", "啮", "镊", "孽"),
        "nin" to listOf("您", "拧"),
        "ning" to listOf("宁", "凝", "拧", "柠", "泞", "佞"),
        "niu" to listOf("牛", "纽", "扭", "钮", "妞"),
        "nong" to listOf("农", "弄", "浓", "脓", "侬"),
        "nou" to listOf("耨"),
        "nu" to listOf("努", "怒", "奴", "弩"),
        "nv" to listOf("女", "衄"),
        "nuan" to listOf("暖", "煖"),
        "nue" to listOf("虐", "疟"),
        "nuo" to listOf("挪", "诺", "糯", "懦", "娜"),
        "o" to listOf("哦", "噢", "喔"),
        "ou" to listOf("欧", "偶", "呕", "藕", "鸥", "禺"),
        "pa" to listOf("怕", "爬", "帕", "扒", "趴", "琶"),
        "pai" to listOf("排", "拍", "派", "牌", "徘", "湃"),
        "pan" to listOf("判", "盘", "盼", "叛", "畔", "潘", "攀"),
        "pang" to listOf("旁", "胖", "庞", "磅", "螃", "彷"),
        "pao" to listOf("跑", "泡", "炮", "抛", "刨", "袍", "瓢"),
        "pei" to listOf("配", "陪", "培", "赔", "佩", "沛", "培", "裴", "霈"),
        "pen" to listOf("喷", "盆", "湓"),
        "peng" to listOf("朋", "碰", "蓬", "棚", "鹏", "彭", "膨", "澎", "捧"),
        "pi" to listOf("皮", "批", "披", "劈", "疲", "僻", "屁", "脾", "琵", "毗", "坯", "痞", "劈"),
        "pian" to listOf("片", "篇", "偏", "骗", "扁", "翩", "骗"),
        "piao" to listOf("票", "飘", "漂", "瓢", "剽", "嫖"),
        "pie" to listOf("撇", "瞥"),
        "pin" to listOf("品", "拼", "贫", "聘", "嫔", "拚"),
        "ping" to listOf("平", "评", "瓶", "凭", "苹", "屏", "萍", "坪", "凭"),
        "po" to listOf("破", "坡", "婆", "迫", "泼", "颇", "泊", "魄", "帕", "叵"),
        "pou" to listOf("剖", " pou", "裒"),
        "pu" to listOf("普", "铺", "扑", "朴", "谱", "浦", "葡", "蒲", "瀑", "菩", "圃", "哺"),
        "qi" to listOf("起", "其", "期", "七", "气", "汽", "棋", "旗", "齐", "奇", "骑", "乞", "企", "启", "弃", "泣", "戚", "迄", "憩", "荠"),
        "qia" to listOf("卡", "恰", "洽", "髂"),
        "qian" to listOf("前", "钱", "千", "签", "浅", "谦", "欠", "牵", "潜", "遣", "迁", "谦", "嵌", "歉", "钳", "虔"),
        "qiang" to listOf("强", "抢", "墙", "枪", "腔", "抢", "羌", "抢", "戗", "镪"),
        "qiao" to listOf("桥", "瞧", "巧", "悄", "敲", "翘", "撬", "乔", "侨", "峭", "俏", "窍"),
        "qie" to listOf("切", "且", "茄", "怯", "妾", "惬", "窃", "锲"),
        "qin" to listOf("亲", "秦", "勤", "琴", "禽", "侵", "擒", "芹", "沁", "寝", "芩"),
        "qing" to listOf("请", "清", "情", "青", "轻", "晴", "庆", "倾", "顷", "氢", "卿", "磬", "蜻"),
        "qiong" to listOf("穷", "琼", "穹", "跫"),
        "qiu" to listOf("秋", "球", "求", "邱", "囚", "酋", "泗", "逑", "巯"),
        "qu" to listOf("去", "区", "取", "曲", "趣", "趋", "屈", "驱", "渠", "躯", "娶", "龋", "觑", "祛"),
        "quan" to listOf("全", "权", "圈", "劝", "泉", "拳", "犬", "券", "诠", "铨", "蜷", "鬈"),
        "que" to listOf("却", "确", "缺", "雀", "鹊", "阙", "炔"),
        "qun" to listOf("群", "裙", "逡"),
        "ran" to listOf("然", "燃", "染", "冉"),
        "rang" to listOf("让", "嚷", "壤", "攘", "瓤"),
        "rao" to listOf("绕", "饶", "饶", "娆"),
        "re" to listOf("热", "惹"),
        "ren" to listOf("人", "认", "任", "忍", "韧", "仁", "刃", "妊", "纫", "饪"),
        "reng" to listOf("仍", "扔"),
        "ri" to listOf("日"),
        "rong" to listOf("容", "融", "荣", "溶", "熔", "蓉", "榕", "茸", "冗"),
        "rou" to listOf("肉", "柔", "揉", "糅"),
        "ru" to listOf("如", "入", "乳", "辱", "儒", "汝", "茹", "濡", "蠕"),
        "ruan" to listOf("软", "阮"),
        "rui" to listOf("锐", "瑞", "睿", "蕊"),
        "run" to listOf("润", "闰"),
        "ruo" to listOf("若", "弱", "偌"),
        "sa" to listOf("撒", "洒", "萨", "仨"),
        "sai" to listOf("赛", "塞", "腮", "噻"),
        "san" to listOf("三", "散", "伞", "叁"),
        "sang" to listOf("桑", "嗓", "丧", "搡"),
        "sao" to listOf("扫", "骚", "嫂", "梢", "臊", "缫"),
        "se" to listOf("色", "塞", "涩", "瑟", "啬"),
        "sen" to listOf("森"),
        "seng" to listOf("僧"),
        "sha" to listOf("沙", "杀", "纱", "傻", "刹", "砂", "煞", "莎", "啥", "霎"),
        "shai" to listOf("晒", "筛"),
        "shan" to listOf("山", "善", "闪", "扇", "陕", "珊", "衫", "擅", "膳", "赡", "汕", "杉"),
        "shang" to listOf("上", "商", "伤", "尚", "赏", "晌", "殇", "裳", "觞"),
        "shao" to listOf("少", "烧", "绍", "稍", "哨", "邵", "梢", "芍", "勺"),
        "she" to listOf("社", "设", "射", "蛇", "舍", "涉", "摄", "奢", "赦", "慑"),
        "shei" to listOf("谁"),
        "shen" to listOf("身", "深", "神", "什", "审", "慎", "申", "伸", "甚", "肾", "渗", "沈", "婶"),
        "sheng" to listOf("生", "声", "省", "胜", "盛", "剩", "圣", "升", "绳", "甥", "牲", "笙"),
        "shi" to listOf("是", "时", "十", "事", "市", "石", "师", "史", "使", "示", "世", "式", "识", "失", "实", "食", "始", "室", "势", "试", "诗", "适", "释", "拾", "逝", "誓", "噬", "匙", "矢"),
        "shou" to listOf("手", "收", "受", "首", "寿", "售", "守", "兽", "瘦", "授", "狩", "艏"),
        "shu" to listOf("书", "数", "树", "属", "术", "输", "束", "暑", "叔", "署", "熟", "鼠", "薯", "梳", "舒", "蔬", "殊", "抒", "蜀", "墅"),
        "shua" to listOf("刷", "耍"),
        "shuai" to listOf("率", "摔", "甩", "帅", "衰", "蟀"),
        "shuan" to listOf("拴", "栓", "涮"),
        "shuang" to listOf("双", "爽", "霜", "孀"),
        "shui" to listOf("水", "说", "睡", "税", "谁", "氺"),
        "shun" to listOf("顺", "瞬", "舜"),
        "shuo" to listOf("说", "硕", "烁", "朔", "勺"),
        "si" to listOf("四", "思", "死", "司", "私", "丝", "撕", "斯", "肆", "寺", "似", "饲", "巳", "泗", "嘶", "肆"),
        "song" to listOf("送", "松", "宋", "颂", "讼", "诵", "耸"),
        "sou" to listOf("搜", "艘", "嗖", "叟", "嗽", "擞"),
        "su" to listOf("速", "素", "诉", "宿", "苏", "俗", "肃", "酥", "粟", "塑", "溯", "夙"),
        "suan" to listOf("算", "酸", "蒜"),
        "sui" to listOf("岁", "随", "碎", "遂", "隧", "髓", "穗", "绥", "隋", "邃"),
        "sun" to listOf("孙", "损", "笋", "狲", "荪"),
        "suo" to listOf("所", "锁", "缩", "索", "琐", "梭", "唢"),
        "ta" to listOf("他", "她", "它", "塔", "踏", "榻", "塌", "獭", "溻", "挞"),
        "tai" to listOf("太", "台", "态", "抬", "泰", "汰", "苔", "肽", "钛"),
        "tan" to listOf("谈", "弹", "叹", "坦", "潭", "摊", "贪", "滩", "坛", "痰", "袒", "碳", "探", "坦", "坍", "昙"),
        "tang" to listOf("堂", "糖", "躺", "汤", "烫", "塘", "膛", "趟", "唐", "倘", "淌", "档", "饧"),
        "tao" to listOf("套", "逃", "讨", "涛", "淘", "陶", "桃", "萄", "滔", "掏", "叨", "饕"),
        "te" to listOf("特", "忒", "忑"),
        "teng" to listOf("疼", "藤", "腾", "誊", "滕"),
        "ti" to listOf("体", "提", "题", "替", "梯", "踢", "蹄", "涕", "剃", "屉", "剔", "惕", "涕", "悌"),
        "tian" to listOf("天", "田", "添", "填", "甜", "恬", "舔", "腆"),
        "tiao" to listOf("条", "调", "跳", "挑", "佻", "眺", "祧"),
        "tie" to listOf("铁", "贴", "帖", "餮"),
        "ting" to listOf("听", "停", "庭", "厅", "挺", "亭", "廷", "艇", "婷", "汀"),
        "tong" to listOf("通", "同", "童", "痛", "统", "铜", "桶", "筒", "桐", "瞳", "侗", "捅"),
        "tou" to listOf("头", "投", "透", "偷", "骰"),
        "tu" to listOf("图", "突", "土", "涂", "途", "吐", "徒", "秃", "秃", "屠", "兔", "吐", "凸"),
        "tuan" to listOf("团", "湍", "抟"),
        "tui" to listOf("推", "退", "腿", "蜕", "褪", "颓", "褪"),
        "tun" to listOf("吞", "屯", "臀", "氽"),
        "tuo" to listOf("拖", "脱", "托", "妥", "椭", "拓", "唾", "陀", "驼"),
        "wa" to listOf("瓦", "挖", "娃", "蛙", "洼", "袜", "哇", "娲"),
        "wai" to listOf("外", "歪", "崴"),
        "wan" to listOf("完", "晚", "万", "碗", "弯", "湾", "顽", "挽", "宛", "婉", "惋", "蔓", "腕", "丸", "芄", "菀"),
        "wang" to listOf("王", "往", "望", "忘", "旺", "网", "亡", "汪", "枉", "妄", "惘", "罔", "辋"),
        "wei" to listOf("为", "位", "未", "维", "卫", "委", "喂", "威", "危", "微", "围", "违", "尾", "胃", "伪", "蔚", "慰", "魏", "畏", "蚊", "韦", "纬", "娓", "痿", "猥"),
        "wen" to listOf("问", "文", "温", "闻", "稳", "吻", "纹", "蚊", "瘟", "雯", "紊", "刎"),
        "weng" to listOf("翁", "嗡", "瓮", "蓊"),
        "wo" to listOf("我", "握", "窝", "卧", "蜗", "涡", "斡", "沃", "龌"),
        "wu" to listOf("五", "无", "物", "午", "武", "务", "误", "吴", "悟", "乌", "污", "屋", "巫", "梧", "吾", "毋", "芜", "伍", "侮", "坞", "戊", "捂", "鹜"),
        "xi" to listOf("系", "西", "息", "希", "喜", "习", "细", "析", "戏", "洗", "溪", "锡", "稀", "夕", "惜", "烯", "欺", "嘻", "膝", "晰", "嬉", "玺", "牺", "曦", "隙", "袭", "奚"),
        "xia" to listOf("下", "夏", "吓", "虾", "瞎", "峡", "瑕", "霞", "侠", "匣", "辖", "遐"),
        "xian" to listOf("现", "先", "线", "县", "显", "闲", "限", "献", "嫌", "宪", "陷", "鲜", "弦", "咸", "馅", "仙", "掀", "纤", "涎", "衔", "腺", "娴", "籼"),
        "xiang" to listOf("想", "向", "相", "香", "象", "项", "响", "享", "像", "巷", "橡", "详", "祥", "翔", "厢", "镶", "享", "饷", "芗"),
        "xiao" to listOf("小", "笑", "晓", "效", "销", "肖", "孝", "校", "消", "宵", "萧", "硝", "霄", "潇", "啸", "啸", "骁"),
        "xie" to listOf("些", "写", "谢", "协", "胁", "斜", "歇", "泄", "屑", "卸", "蟹", "邪", "携", "鞋", "谐", "挟", "懈", "泄", "泻", "绁", "榭"),
        "xin" to listOf("新", "心", "信", "薪", "辛", "欣", "馨", "锌", "芯", "衅", "忻", "莘"),
        "xing" to listOf("行", "星", "兴", "形", "型", "姓", "幸", "性", "醒", "腥", "刑", "杏", "邢", "荥", "硎", "猩"),
        "xiong" to listOf("兄", "凶", "胸", "熊", "凶", "汹", "匈", "雄"),
        "xiu" to listOf("修", "休", "羞", "袖", "秀", "锈", "嗅", "绣", "朽", "咻", "馐", "髅"),
        "xu" to listOf("需", "许", "续", "虚", "序", "须", "叙", "畜", "蓄", "徐", "恤", "絮", "旭", "墟", "栩", "戌", "胥"),
        "xuan" to listOf("选", "宣", "悬", "旋", "玄", "轩", "喧", "炫", "眩", "绚", "渲", "萱", "漩", "铉"),
        "xue" to listOf("学", "雪", "血", "穴", "靴", "薛", "削", "谑", "踅"),
        "xun" to listOf("训", "寻", "迅", "巡", "讯", "逊", "熏", "循", "旬", "询", "殉", "勋", "巽", "驯", "薰"),
        "ya" to listOf("呀", "压", "牙", "亚", "雅", "鸭", "崖", "押", "鸦", "哑", "讶", "涯", "衙", "蚜", "娅", "崖", "涯"),
        "yan" to listOf("研", "言", "眼", "烟", "严", "炎", "延", "盐", "岩", "颜", "宴", "艳", "掩", "燕", "衍", "淹", "焉", "厌", "彦", "雁", "焰", "偃", "延", "腌", "阉", "闫"),
        "yang" to listOf("样", "阳", "洋", "养", "羊", "扬", "仰", "央", "氧", "痒", "疡", "漾", "殃", "鸯", "蛘", "恙"),
        "yao" to listOf("要", "药", "邀", "摇", "遥", "腰", "妖", "钥", "咬", "耀", "尧", "肴", "姚", "杳", "舀"),
        "ye" to listOf("也", "页", "业", "夜", "叶", "野", "液", "咽", "烨", "拽", "曳", "椰"),
        "yi" to listOf("一", "以", "已", "意", "义", "易", "医", "艺", "益", "忆", "移", "异", "伊", "依", "疑", "仪", "宜", "姨", "倚", "蚁", "乙", "亦", "役", "逸", "逸", "疫", "亦", "忆", "谊", "议", "译", "翼", "溢"),
        "yin" to listOf("因", "音", "银", "印", "引", "饮", "阴", "瘾", "姻", "寅", "吟", "淫", "荫", "殷", "鄄", "垠", "蚓"),
        "ying" to listOf("应", "英", "营", "影", "迎", "硬", "营", "婴", "莹", "鹰", "蝇", "樱", "盈", "颖", "莹", "荧", "萤", "营", "赢", "瀛", "萦", "莺"),
        "yo" to listOf("哟", "唷"),
        "yong" to listOf("用", "永", "拥", "勇", "涌", "咏", "庸", "泳", "俑", "俑", "蛹", "踊", "咏", "俑", "墉", "壅", "臃"),
        "you" to listOf("有", "又", "由", "油", "右", "友", "尤", "幼", "忧", "悠", "优", "幽", "邮", "酉", "诱", "游", "釉", "柚", "鱿", "犹", "尤", "疣"),
        "yu" to listOf("于", "与", "语", "遇", "余", "育", "玉", "鱼", "雨", "欲", "裕", "愈", "宇", "预", "域", "誉", "愉", "愚", "渔", "娱", "予", "逾", "渝", "虞", "愚", "舆", "淤", "瑜", "逾", "喻", "渝", "隅", "峪", "驭", "吁", "禹"),
        "yuan" to listOf("元", "原", "远", "院", "员", "圆", "缘", "源", "怨", "袁", "园", "猿", "圆", "垣", "援", "怨", "院", "苑", "愿", "渊", "冤", "宛", "婉", "沅", "瑗", "辕", "垣"),
        "yue" to listOf("月", "约", "越", "跃", "悦", "岳", "钥", "阅", "粤", "曰", "钺", "跃", "瀹", "栎"),
        "yun" to listOf("运", "云", "晕", "韵", "匀", "允", "蕴", "韵", "耘", "陨", "蕴", "熨", "郓", "恽"),
        "za" to listOf("杂", "砸", "咋", "咂"),
        "zai" to listOf("在", "再", "载", "灾", "栽", "宰", "崽", "哉", "裁"),
        "zan" to listOf("咱", "赞", "暂", "攒", "拶", "瓒"),
        "zang" to listOf("脏", "葬", "藏", "赃", "臧"),
        "zao" to listOf("早", "造", "糟", "遭", "燥", "灶", "皂", "凿", "藻", "躁"),
        "ze" to listOf("则", "责", "泽", "择", "咋", "啧", "箦"),
        "zei" to listOf("贼"),
        "zen" to listOf("怎"),
        "zeng" to listOf("增", "赠", "憎", "曾", "甑"),
        "zha" to listOf("炸", "扎", "闸", "渣", "咋", "乍", "榨", "吒", "哑", "蚱", "栅"),
        "zhai" to listOf("宅", "窄", "债", "寨", "摘", "斋", "翟"),
        "zhan" to listOf("站", "战", "展", "占", "沾", "粘", "斩", "崭", "盏", "湛", "瞻", "绽", "栈"),
        "zhang" to listOf("张", "章", "长", "障", "掌", "涨", "帐", "仗", "杖", "彰", "樟", "瘴", "障", "嶂", "胀"),
        "zhao" to listOf("找", "照", "招", "着", "赵", "兆", "召", "朝", "沼", "罩", "爪", "诏", "肇", "钊"),
        "zhe" to listOf("这", "着", "者", "浙", "哲", "折", "遮", "蔗", "辙", "褶", "蛰", "辄", "谪"),
        "zhei" to listOf("这"),
        "zhen" to listOf("真", "阵", "镇", "震", "针", "珍", "诊", "枕", "侦", "贞", "砧", "斟", "圳", "振", "朕", "祯"),
        "zheng" to listOf("正", "整", "证", "政", "争", "征", "症", "郑", "蒸", "睁", "铮", "筝", "拯", "怔", "峥"),
        "zhi" to listOf("只", "之", "知", "直", "制", "指", "治", "质", "值", "职", "至", "支", "止", "志", "织", "纸", "枝", "脂", "汁", "芝", "吱", "址", "痔", "滞", "挚", "掷", "殖", "峙", "炙", "踯", "栀", "趾", "咫", "轵", "祉"),
        "zhong" to listOf("中", "种", "重", "众", "终", "钟", "忠", "肿", "仲", "冢", "锺", "螽", "盅", "衷"),
        "zhou" to listOf("周", "州", "轴", "洲", "粥", "皱", "咒", "肘", "帚", "纣", "宙", "咒", "昼", "胄", "绉"),
        "zhu" to listOf("主", "住", "注", "助", "住", "朱", "猪", "竹", "祝", "株", "珠", "筑", "蛛", "烛", "逐", "铸", "煮", "瞩", "嘱", "贮", "拄", "蛀", "竺", "瘃"),
        "zhua" to listOf("抓", "爪"),
        "zhuai" to listOf("拽"),
        "zhuan" to listOf("转", "专", "赚", "砖", "撰", "篆", "啭", "馔"),
        "zhuang" to listOf("装", "撞", "壮", "状", "桩", "妆", "幢"),
        "zhui" to listOf("追", "坠", "缀", "椎", "赘", "锥", "缒"),
        "zhun" to listOf("准", "肫", "谆"),
        "zhuo" to listOf("捉", "桌", "着", "拙", "浊", "酌", "灼", "卓", "镯", "啄", "琢", "茁", "浞", "斫"),
        "zi" to listOf("子", "字", "自", "资", "紫", "滋", "姊", "咨", "姿", "兹", "孜", "梓", "籽", "秭", "辎", "淄", "缁", "谘", "孳"),
        "zong" to listOf("总", "从", "宗", "综", "棕", "踪", "鬃", "粽", "腙", "纵", "偬"),
        "zou" to listOf("走", "奏", "揍", "邹", "陬", "驺"),
        "zu" to listOf("组", "足", "族", "祖", "阻", "租", "诅", "足", "卒", "镞"),
        "zuan" to listOf("钻", "纂", "赚", "攥", "缵"),
        "zui" to listOf("最", "罪", "嘴", "醉", "咀", "觜", "蕞"),
        "zun" to listOf("尊", "遵", "樽", "鳟", "撙"),
        "zuo" to listOf("做", "作", "左", "坐", "昨", "佐", "凿", "琢", "柞", "阝", "胙")
    )

    /** 词语字典：完整拼音 → 词语 */
    private val wordMap: Map<String, List<String>> = mapOf(
        "nihao" to listOf("你好", "倪浩"),
        "xiexie" to listOf("谢谢"),
        "zaijian" to listOf("再见"),
        "duibuqi" to listOf("对不起"),
        "meiguanxi" to listOf("没关系"),
        "zhongguo" to listOf("中国"),
        "beijing" to listOf("北京"),
        "shanghai" to listOf("上海"),
        "guangzhou" to listOf("广州"),
        "shenzhen" to listOf("深圳"),
        "weishenme" to listOf("为什么"),
        "zenmeyang" to listOf("怎么样"),
        "shime" to listOf("什么"),
        "zenme" to listOf("怎么"),
        "yinwei" to listOf("因为"),
        "suoyi" to listOf("所以"),
        "ruguo" to listOf("如果"),
        "suiran" to listOf("虽然"),
        "danshi" to listOf("但是"),
        "buhao" to listOf("不好"),
        "haochi" to listOf("好吃"),
        "haokan" to listOf("好看"),
        "haoting" to listOf("好听"),
        "haowan" to listOf("好玩"),
        "xiwang" to listOf("希望"),
        "xihuan" to listOf("喜欢"),
        "mingbai" to listOf("明白"),
        "dongle" to listOf("懂了"),
        "zhidao" to listOf("知道", "直到"),
        "buzhidao" to listOf("不知道"),
        "meiyou" to listOf("没有"),
        "buyong" to listOf("不用"),
        "bukeqi" to listOf("不客气"),
        "buhaoyisi" to listOf("不好意思"),
        "meishi" to listOf("没事"),
        "meiwenti" to listOf("没问题"),
        "tongyi" to listOf("同意", "统一"),
        "fandui" to listOf("反对"),
        "juede" to listOf("觉得"),
        "renwei" to listOf("认为"),
        "kaoshi" to listOf("考试"),
        "xuexi" to listOf("学习"),
        "gongzuo" to listOf("工作"),
        "shenghuo" to listOf("生活"),
        "shijian" to listOf("时间", "事件"),
        "pengyou" to listOf("朋友"),
        "laoshi" to listOf("老师"),
        "xuesheng" to listOf("学生"),
        "baba" to listOf("爸爸"),
        "mama" to listOf("妈妈"),
        "gege" to listOf("哥哥"),
        "didu" to listOf("弟弟"),
        "jiejie" to listOf("姐姐"),
        "meimei" to listOf("妹妹"),
        "yiqi" to listOf("一起"),
        "yihou" to listOf("以后"),
        "yiqian" to listOf("以前"),
        "yuelai" to listOf("越来越"),
        "feichang" to listOf("非常"),
        "tongshi" to listOf("同事"),
        "laoban" to listOf("老板"),
        "qingwen" to listOf("请问"),
        "ting" to listOf("听"),
        "kandao" to listOf("看到"),
        "zhongyao" to listOf("重要"),
        "tamen" to listOf("他们"),
        "women" to listOf("我们"),
        "nimen" to listOf("你们"),
        "shuole" to listOf("说了"),
        "zuole" to listOf("做了"),
        "chile" to listOf("吃了"),
        "hele" to listOf("喝了"),
        "mle" to listOf("买了"),
        "maile" to listOf("买了", "卖了"),
        "daole" to listOf("到了"),
        "huile" to listOf("回了"),
        "zoule" to listOf("走了"),
        "laile" to listOf("来了"),
        "kanguo" to listOf("看过"),
        "zuoguo" to listOf("做过"),
        "chiguo" to listOf("吃过"),
        "heguo" to listOf("喝过"),
        "maiguo" to listOf("买过"),
        "quguo" to listOf("去过"),

        // === 常用动词 ===
        "kaishi" to listOf("开始"),
        "jixu" to listOf("继续"),
        "wancheng" to listOf("完成"),
        "faxian" to listOf("发现"),
        "jueding" to listOf("决定"),
        "xuyao" to listOf("需要"),
        "gaosu" to listOf("告诉"),
        "huida" to listOf("回答"),
        "bangzhu" to listOf("帮助"),
        "zhaogu" to listOf("照顾"),
        "guanxin" to listOf("关心"),
        "yuanliang" to listOf("原谅"),
        "baorong" to listOf("包容"),
        "jieshao" to listOf("介绍"),
        "jieshi" to listOf("解释"),
        "shuoming" to listOf("说明"),
        "chengren" to listOf("承认"),
        "fouren" to listOf("否认"),
        "xuanze" to listOf("选择"),
        "tiaoxuan" to listOf("挑选"),
        "zhanshi" to listOf("展示"),
        "biaoda" to listOf("表达"),
        "biaoshi" to listOf("表示"),
        "biaoxian" to listOf("表现"),
        "sikao" to listOf("思考"),
        "kaolv" to listOf("考虑"),
        "chuli" to listOf("处理"),
        "jiejue" to listOf("解决"),
        "banfa" to listOf("办法"),
        "chansheng" to listOf("产生"),
        "fasheng" to listOf("发生"),
        "jingli" to listOf("经历"),
        "jingyan" to listOf("经验"),
        "taolun" to listOf("讨论"),
        "jiaoliu" to listOf("交流"),
        "goutong" to listOf("沟通"),
        "pingjia" to listOf("评价"),
        "jianyi" to listOf("建议"),

        // === 常用名词 ===
        "shihou" to listOf("时候"),
        "difang" to listOf("地方"),
        "wenti" to listOf("问题"),
        "shiqing" to listOf("事情"),
        "shijie" to listOf("世界"),
        "guojia" to listOf("国家"),
        "shehui" to listOf("社会"),
        "dianhua" to listOf("电话"),
        "diannao" to listOf("电脑"),
        "shouji" to listOf("手机"),
        "wangluo" to listOf("网络"),
        "xinxi" to listOf("信息"),
        "shuju" to listOf("数据"),
        "daan" to listOf("答案"),
        "jieguo" to listOf("结果"),
        "yuanyin" to listOf("原因"),
        "guocheng" to listOf("过程"),
        "fangfa" to listOf("方法"),
        "fangshi" to listOf("方式"),
        "fangxiang" to listOf("方向"),
        "weizhi" to listOf("位置"),
        "didian" to listOf("地点"),
        "kongjian" to listOf("空间"),
        "fanwei" to listOf("范围"),
        "yuyan" to listOf("语言"),
        "yufa" to listOf("语法"),
        "ciyu" to listOf("词语"),
        "juzi" to listOf("句子"),
        "wenzhang" to listOf("文章"),
        "xinwen" to listOf("新闻"),
        "xiaoxi" to listOf("消息"),
        "tongzhi" to listOf("通知"),
        "gonggao" to listOf("公告"),
        "guanggao" to listOf("广告"),
        "biaoqing" to listOf("表情"),
        "qinggan" to listOf("情感"),
        "qingxu" to listOf("情绪"),
        "xinqing" to listOf("心情"),
        "xinli" to listOf("心理"),
        "sixiang" to listOf("思想"),
        "xingge" to listOf("性格"),
        "nianling" to listOf("年龄"),
        "shengao" to listOf("身高"),
        "tizhong" to listOf("体重"),

        // === 常用形容词 ===
        "kuaile" to listOf("快乐"),
        "xingfu" to listOf("幸福"),
        "meili" to listOf("美丽"),
        "congming" to listOf("聪明"),
        "qinfen" to listOf("勤奋"),
        "nuli" to listOf("努力"),
        "youxiu" to listOf("优秀"),
        "weida" to listOf("伟大"),
        "jiandan" to listOf("简单"),
        "fuza" to listOf("复杂"),
        "zhenshi" to listOf("真实"),
        "shiji" to listOf("实际"),
        "jianjie" to listOf("简介"),
        "xiangxi" to listOf("详细"),
        "pingfan" to listOf("平凡"),
        "pingjing" to listOf("平静"),
        "anjing" to listOf("安静"),
        "putong" to listOf("普通"),
        "tebie" to listOf("特别"),
        "zhengchang" to listOf("正常"),
        "jiankang" to listOf("健康"),
        "weixian" to listOf("危险"),
        "anquan" to listOf("安全"),
        "yonggan" to listOf("勇敢"),
        "pianyi" to listOf("便宜"),
        "rongyi" to listOf("容易"),
        "kunnan" to listOf("困难"),

        // === 常用表达 ===
        "meishenme" to listOf("没什么"),
        "meiyou" to listOf("没有"),

        // === 时间词 ===
        "jintian" to listOf("今天"),
        "mingtian" to listOf("明天"),
        "zuotian" to listOf("昨天"),
        "xianzai" to listOf("现在"),
        "zaoshang" to listOf("早上"),
        "wanshang" to listOf("晚上"),
        "zhongwu" to listOf("中午"),
        "xiawu" to listOf("下午"),
        "shangwu" to listOf("上午"),
        "zhiqian" to listOf("之前"),
        "zhihou" to listOf("之后"),
        "zhongjian" to listOf("中间"),
        "mashang" to listOf("马上"),
        "like" to listOf("立刻"),
        "turan" to listOf("突然"),
        "huran" to listOf("忽然"),
        "zhongyu" to listOf("终于"),
        "zuihou" to listOf("最后"),
        "shouxian" to listOf("首先"),
        "qici" to listOf("其次"),
        "ranhou" to listOf("然后"),
        "yijing" to listOf("已经"),
        "zhengzai" to listOf("正在"),
        "gangcai" to listOf("刚才"),
        "yizhi" to listOf("一直"),
        "jingchang" to listOf("经常"),
        "changchang" to listOf("常常"),
        "tongchang" to listOf("通常"),
        "youshi" to listOf("有时"),
        "ouer" to listOf("偶尔"),
        "yongyuan" to listOf("永远"),
        "zanshi" to listOf("暂时"),
        "changqi" to listOf("长期"),
        "duanqi" to listOf("短期"),
        "qijian" to listOf("期间"),

        // === 人物 ===
        "tongxue" to listOf("同学"),
        "jiaren" to listOf("家人"),
        "fumu" to listOf("父母"),
        "xiongdi" to listOf("兄弟"),
        "jiemei" to listOf("姐妹"),
        "haizi" to listOf("孩子"),
        "xiansheng" to listOf("先生"),
        "nvshi" to listOf("女士"),
        "xiaojie" to listOf("小姐"),
        "taitai" to listOf("太太"),
        "xiaohai" to listOf("小孩"),
        "qingnian" to listOf("青年"),
        "shaonian" to listOf("少年"),
        "ertong" to listOf("儿童"),
        "laoren" to listOf("老人"),
        "jiaoshi" to listOf("教师"),
        "jiaoshou" to listOf("教授"),
        "zuojia" to listOf("作家"),
        "huajia" to listOf("画家"),
        "yanyuan" to listOf("演员"),
        "geshou" to listOf("歌手"),
        "daoyan" to listOf("导演"),
        "jingcha" to listOf("警察"),
        "junren" to listOf("军人"),
        "yisheng" to listOf("医生"),
        "hushi" to listOf("护士"),
        "lushi" to listOf("律师"),
        "shangren" to listOf("商人"),
        "nongmin" to listOf("农民"),
        "gongren" to listOf("工人"),

        // === 自然 ===
        "tianqi" to listOf("天气"),
        "taiyang" to listOf("太阳"),
        "yueliang" to listOf("月亮"),
        "xingxing" to listOf("星星"),
        "huaduo" to listOf("花朵"),
        "shumu" to listOf("树木"),
        "heliu" to listOf("河流"),
        "shanchuan" to listOf("山川"),
        "haiyang" to listOf("海洋"),

        // === 情绪 ===
        "kaixin" to listOf("开心"),
        "nanguo" to listOf("难过"),
        "shengqi" to listOf("生气"),
        "haipa" to listOf("害怕"),
        "jinzhang" to listOf("紧张"),
        "jidong" to listOf("激动"),
        "gandong" to listOf("感动"),
        "jingya" to listOf("惊讶"),
        "yongqi" to listOf("勇气"),

        // === 疑问词 ===
        "shenme" to listOf("什么"),
        "nali" to listOf("哪里"),
        "nage" to listOf("哪个"),
        "duoshao" to listOf("多少"),
        "jige" to listOf("几个"),
        "shuide" to listOf("谁的"),

        // === 连词 ===
        "erqie" to listOf("而且"),
        "huozhe" to listOf("或者"),
        "haishi" to listOf("还是"),
        "budan" to listOf("不但"),

        // === 助动词/副词 ===
        "keyi" to listOf("可以"),
        "yinggai" to listOf("应该"),
        "bixu" to listOf("必须"),
        "keneng" to listOf("可能"),
        "yexu" to listOf("也许"),
        "dagai" to listOf("大概"),
        "dangran" to listOf("当然"),
        "queshi" to listOf("确实"),
        "haoxiang" to listOf("好像"),
        "sihu" to listOf("似乎"),

        // === 国家/地区/语言 ===
        "meiguo" to listOf("美国"),
        "riben" to listOf("日本"),
        "hanguo" to listOf("韩国"),
        "ouzhou" to listOf("欧洲"),
        "yazhou" to listOf("亚洲"),
        "yingyu" to listOf("英语"),
        "zhongwen" to listOf("中文"),
        "wenhua" to listOf("文化"),
        "guoji" to listOf("国际"),
        "guonei" to listOf("国内"),
        "guowai" to listOf("国外"),

        // === 学科/领域 ===
        "jingji" to listOf("经济"),
        "keji" to listOf("科技"),
        "jiaoyu" to listOf("教育"),
        "yishu" to listOf("艺术"),
        "yinyue" to listOf("音乐"),
        "dianying" to listOf("电影"),
        "dianshi" to listOf("电视"),
        "xiaoshuo" to listOf("小说"),
        "baozhi" to listOf("报纸"),
        "zazhi" to listOf("杂志"),
        "xueshu" to listOf("学术"),
        "kexue" to listOf("科学"),
        "zhexue" to listOf("哲学"),
        "wuli" to listOf("物理"),
        "huaxue" to listOf("化学"),
        "shuxue" to listOf("数学"),
        "lishi" to listOf("历史"),
        "dili" to listOf("地理"),
        "shengwu" to listOf("生物"),
        "zhengzhi" to listOf("政治"),

        // === 场所/建筑 ===
        "xuexiao" to listOf("学校"),
        "yiyuan" to listOf("医院"),
        "yinhang" to listOf("银行"),
        "shangdian" to listOf("商店"),
        "chaoshi" to listOf("超市"),
        "fandian" to listOf("饭店"),
        "jiudian" to listOf("酒店"),
        "gongyuan" to listOf("公园"),
        "tushuguan" to listOf("图书馆"),
        "bowuguan" to listOf("博物馆"),
        "dizhi" to listOf("地址"),

        // === 交通 ===
        "feiji" to listOf("飞机"),
        "huoche" to listOf("火车"),
        "qiche" to listOf("汽车"),
        "ditie" to listOf("地铁"),
        "gongjiaoche" to listOf("公交车"),
        "zixingche" to listOf("自行车"),
        "jiaotong" to listOf("交通"),
        "tongxun" to listOf("通讯"),

        // === 运动/比赛 ===
        "yundong" to listOf("运动"),
        "zuqiu" to listOf("足球"),
        "lanqiu" to listOf("篮球"),
        "paiqiu" to listOf("排球"),
        "wangqiu" to listOf("网球"),
        "youyong" to listOf("游泳"),
        "bisai" to listOf("比赛"),
        "shengli" to listOf("胜利"),
        "shibai" to listOf("失败"),
        "chengji" to listOf("成绩"),

        // === 社会/政治/经济 ===
        "zhengfu" to listOf("政府"),
        "zhengce" to listOf("政策"),
        "falv" to listOf("法律"),
        "minzu" to listOf("民族"),
        "heping" to listOf("和平"),
        "zhanzheng" to listOf("战争"),
        "fazhan" to listOf("发展"),
        "jinbu" to listOf("进步"),
        "gaige" to listOf("改革"),
        "kaifang" to listOf("开放"),
        "chuangxin" to listOf("创新"),
        "yanjiu" to listOf("研究"),
        "jihua" to listOf("计划"),
        "guanli" to listOf("管理"),
        "zuzhi" to listOf("组织"),
        "tuandui" to listOf("团队"),
        "hezuo" to listOf("合作"),
        "jingzheng" to listOf("竞争"),
        "shengchan" to listOf("生产"),
        "xiaofei" to listOf("消费"),
        "jiage" to listOf("价格"),
        "shichang" to listOf("市场"),
        "jinrong" to listOf("金融"),
        "gupiao" to listOf("股票"),
        "touzi" to listOf("投资"),
        "chengshi" to listOf("城市", "诚实"),
        "nongcun" to listOf("农村"),

        // === 环境/资源 ===
        "huanjing" to listOf("环境"),
        "baohu" to listOf("保护"),
        "wuran" to listOf("污染"),
        "ziyuan" to listOf("资源"),
        "nengyuan" to listOf("能源"),
        "dianli" to listOf("电力"),

        // === 计算机/技术 ===
        "jisuanji" to listOf("计算机"),
        "ruanjian" to listOf("软件"),
        "yingjian" to listOf("硬件"),
        "xitong" to listOf("系统"),
        "chengxu" to listOf("程序"),
        "sheji" to listOf("设计"),
        "kaifa" to listOf("开发"),
        "ceshi" to listOf("测试"),
        "weihu" to listOf("维护"),
        "yunying" to listOf("运营"),
        "fuwu" to listOf("服务"),
        "chanpin" to listOf("产品"),
        "kehu" to listOf("客户"),
        "yonghu" to listOf("用户"),
        "tiyan" to listOf("体验"),
        "wangzhan" to listOf("网站"),
        "wangzhi" to listOf("网址"),
        "youjian" to listOf("邮件"),
        "youxiang" to listOf("邮箱"),

        // === 学习相关 ===
        "kecheng" to listOf("课程"),
        "ketang" to listOf("课堂"),
        "keben" to listOf("课本"),
        "jiangke" to listOf("讲课"),
        "jiangzuo" to listOf("讲座"),
        "jiangshi" to listOf("讲师"),
        "jiaolian" to listOf("教练"),
        "jiaoxue" to listOf("教学"),

        // === 方位 ===
        "pangbian" to listOf("旁边"),
        "fujin" to listOf("附近"),
        "zhouwei" to listOf("周围"),
        "waimian" to listOf("外面"),
        "limian" to listOf("里面"),
        "shangmian" to listOf("上面"),
        "xiamian" to listOf("下面"),
        "qianmian" to listOf("前面"),
        "houmian" to listOf("后面"),
        "zuobian" to listOf("左边"),
        "youbian" to listOf("右边"),
        "dongbian" to listOf("东边"),
        "xibian" to listOf("西边"),
        "nanbian" to listOf("南边"),
        "beibian" to listOf("北边"),

        // === 数量词 ===
        "yixie" to listOf("一些"),
        "henduo" to listOf("很多"),
        "xuduo" to listOf("许多"),
        "yiqie" to listOf("一切"),
        "quanbu" to listOf("全部"),
        "bufen" to listOf("部分"),
        "yiban" to listOf("一般"),
        "naixin" to listOf("耐心"),
        "xinxin" to listOf("信心"),

        // === 高频双字词（按使用频率排序）===
        // 人称/关系
        "nide" to listOf("你的"),
        "wode" to listOf("我的"),
        "tade" to listOf("他的", "她的"),
        "nide" to listOf("你的"),
        "tamen" to listOf("他们", "她们"),
        "ziji" to listOf("自己"),
        "bieren" to listOf("别人"),
        "dajia" to listOf("大家"),
        "bidao" to listOf("比较"),

        // 常用动词短语
        "zaijian" to listOf("再见"),
        "keyi" to listOf("可以"),
        "nenggou" to listOf("能够"),
        "yijing" to listOf("已经"),
        "zhengzai" to listOf("正在"),
        "yizhi" to listOf("一直"),
        "yiqian" to listOf("以前", "一起"),
        "yihou" to listOf("以后"),
        "yiqi" to listOf("一起"),
        "shangqu" to listOf("上去"),
        "xiaqu" to listOf("下去"),
        "guoqu" to listOf("过去"),
        "chulai" to listOf("出来"),
        "jinlai" to listOf("进来"),
        "huilai" to listOf("回来"),
        "guolai" to listOf("过来"),
        "qilai" to listOf("起来"),
        "xiaqu" to listOf("下去"),

        // 常用名词短语
        "shihou" to listOf("时候"),
        "difang" to listOf("地方"),
        "shiqing" to listOf("事情"),
        "wenti" to listOf("问题"),
        "yiyang" to listOf("一样"),
        "zenmeyang" to listOf("怎么样"),
        "shenme" to listOf("什么"),
        "zenme" to listOf("怎么"),
        "weishenme" to listOf("为什么"),
        "yinwei" to listOf("因为"),
        "suoyi" to listOf("所以"),
        "ruguo" to listOf("如果"),
        "suiran" to listOf("虽然"),
        "danshi" to listOf("但是"),
        "erqie" to listOf("而且"),
        "huozhe" to listOf("或者"),
        "haishi" to listOf("还是"),
        "budan" to listOf("不但"),
        "erqie" to listOf("而且"),

        // 时间/频率
        "xianzai" to listOf("现在"),
        "jintian" to listOf("今天"),
        "mingtian" to listOf("明天"),
        "zuotian" to listOf("昨天"),
        "yihou" to listOf("以后"),
        "yiqian" to listOf("以前"),
        "zhongyu" to listOf("终于"),
        "mashang" to listOf("马上"),
        "like" to listOf("立刻"),
        "turan" to listOf("突然"),
        "huran" to listOf("忽然"),
        "yizhi" to listOf("一直"),
        "jingchang" to listOf("经常"),
        "changchang" to listOf("常常"),
        "tongchang" to listOf("通常"),
        "youshi" to listOf("有时"),
        "ouer" to listOf("偶尔"),
        "yongyuan" to listOf("永远"),

        // 常用形容词
        "haochi" to listOf("好吃"),
        "haokan" to listOf("好看"),
        "haoting" to listOf("好听"),
        "haowan" to listOf("好玩"),
        "haoxiang" to listOf("好像"),
        "nanguo" to listOf("难过"),
        "kaixin" to listOf("开心"),
        "shengqi" to listOf("生气"),
        "haipa" to listOf("害怕"),
        "jinzhang" to listOf("紧张"),
        "jidong" to listOf("激动"),
        "gandong" to listOf("感动"),
        "jingya" to listOf("惊讶"),
        "kuaile" to listOf("快乐"),
        "xingfu" to listOf("幸福"),
        "meili" to listOf("美丽"),
        "congming" to listOf("聪明"),
        "youxiu" to listOf("优秀"),
        "jiandan" to listOf("简单"),
        "fuza" to listOf("复杂"),
        "pingfan" to listOf("平凡"),
        "anjing" to listOf("安静"),
        "putong" to listOf("普通"),
        "tebie" to listOf("特别"),
        "jiankang" to listOf("健康"),
        "weixian" to listOf("危险"),
        "anquan" to listOf("安全"),
        "rongyi" to listOf("容易"),
        "kunnan" to listOf("困难"),

        // 常用动词
        "kaishi" to listOf("开始"),
        "jixu" to listOf("继续"),
        "wancheng" to listOf("完成"),
        "faxian" to listOf("发现"),
        "jueding" to listOf("决定"),
        "xuyao" to listOf("需要"),
        "gaosu" to listOf("告诉"),
        "huida" to listOf("回答"),
        "xiangdao" to listOf("想到"),
        "zhidao" to listOf("知道", "直到"),
        "juede" to listOf("觉得"),
        "renwei" to listOf("认为"),
        "xiwang" to listOf("希望"),
        "xihuan" to listOf("喜欢"),
        "mingbai" to listOf("明白"),
        "dongle" to listOf("懂了"),
        "xuanze" to listOf("选择"),
        "sikao" to listOf("思考"),
        "kaolv" to listOf("考虑"),
        "chuli" to listOf("处理"),
        "jiejue" to listOf("解决"),
        "taolun" to listOf("讨论"),
        "jiaoliu" to listOf("交流"),
        "goutong" to listOf("沟通"),
        "jianyi" to listOf("建议"),
        "biaoda" to listOf("表达"),
        "biaoshi" to listOf("表示"),
        "chansheng" to listOf("产生"),
        "fasheng" to listOf("发生"),
        "jingli" to listOf("经历"),
        "jingyan" to listOf("经验"),

        // 常用名词
        "shijian" to listOf("时间", "事件"),
        "pengyou" to listOf("朋友"),
        "laoshi" to listOf("老师"),
        "xuesheng" to listOf("学生"),
        "tongshi" to listOf("同事"),
        "laoban" to listOf("老板"),
        "qingwen" to listOf("请问"),
        "guanxi" to listOf("关系"),
        "yisi" to listOf("意思"),
        "yiyi" to listOf("意义", "异议"),
        "ganqing" to listOf("感情"),
        "weihao" to listOf("爱好"),
        "xingqu" to listOf("兴趣"),
        "mubiao" to listOf("目标"),
        "lixiang" to listOf("理想"),
        "mengxiang" to listOf("梦想"),
        "jihua" to listOf("计划"),
        "zhunbei" to listOf("准备"),
        "jieguo" to listOf("结果"),
        "yuanyin" to listOf("原因"),
        "guocheng" to listOf("过程"),
        "fangfa" to listOf("方法"),
        "fangshi" to listOf("方式"),
        "fangxiang" to listOf("方向"),
        "weizhi" to listOf("位置"),
        "didian" to listOf("地点"),
        "kongjian" to listOf("空间"),
        "fanwei" to listOf("范围"),
        "yuyan" to listOf("语言"),
        "juzi" to listOf("句子"),
        "xinwen" to listOf("新闻"),
        "xiaoxi" to listOf("消息"),
        "tongzhi" to listOf("通知"),
        "dianhua" to listOf("电话"),
        "diannao" to listOf("电脑"),
        "shouji" to listOf("手机"),
        "wangluo" to listOf("网络"),
        "xinxi" to listOf("信息"),
        "shuju" to listOf("数据"),
        "daan" to listOf("答案"),

        // 场所
        "xuexiao" to listOf("学校"),
        "yiyuan" to listOf("医院"),
        "yinhang" to listOf("银行"),
        "shangdian" to listOf("商店"),
        "chaoshi" to listOf("超市"),
        "fandian" to listOf("饭店"),
        "jiudian" to listOf("酒店"),
        "gongyuan" to listOf("公园"),
        "tushuguan" to listOf("图书馆"),
        "bowuguan" to listOf("博物馆"),

        // 自然/天气
        "tianqi" to listOf("天气"),
        "taiyang" to listOf("太阳"),
        "yueliang" to listOf("月亮"),
        "xingxing" to listOf("星星"),
        "shumu" to listOf("树木"),
        "heliu" to listOf("河流"),
        "haiyang" to listOf("海洋"),
        "shan" to listOf("山"),
        "shui" to listOf("水"),
        "huo" to listOf("火"),
        "tu" to listOf("土"),
        "mu" to listOf("木"),
        "jin" to listOf("金"),

        // 交通
        "feiji" to listOf("飞机"),
        "huoche" to listOf("火车"),
        "qiche" to listOf("汽车"),
        "ditie" to listOf("地铁"),
        "gongjiaoche" to listOf("公交车"),
        "zixingche" to listOf("自行车"),
        "jiaotong" to listOf("交通"),

        // 情绪
        "yongqi" to listOf("勇气"),

        // 助动词
        "yinggai" to listOf("应该"),
        "bixu" to listOf("必须"),
        "keneng" to listOf("可能"),
        "yexu" to listOf("也许"),
        "dagai" to listOf("大概"),
        "dangran" to listOf("当然"),
        "queshi" to listOf("确实"),
        "sihu" to listOf("似乎"),

        // 学科
        "jingji" to listOf("经济"),
        "keji" to listOf("科技"),
        "jiaoyu" to listOf("教育"),
        "yishu" to listOf("艺术"),
        "yinyue" to listOf("音乐"),
        "dianying" to listOf("电影"),
        "dianshi" to listOf("电视"),
        "xiaoshuo" to listOf("小说"),
        "kexue" to listOf("科学"),
        "lishi" to listOf("历史"),
        "shuxue" to listOf("数学"),
        "wuli" to listOf("物理"),
        "huaxue" to listOf("化学"),
        "shengwu" to listOf("生物"),

        // 社会
        "shehui" to listOf("社会"),
        "guojia" to listOf("国家"),
        "zhengfu" to listOf("政府"),
        "falv" to listOf("法律"),
        "minzu" to listOf("民族"),
        "heping" to listOf("和平"),
        "zhanzheng" to listOf("战争"),
        "fazhan" to listOf("发展"),
        "jinbu" to listOf("进步"),
        "gaige" to listOf("改革"),
        "kaifang" to listOf("开放"),
        "chuangxin" to listOf("创新"),
        "yanjiu" to listOf("研究"),
        "guanli" to listOf("管理"),
        "zuzhi" to listOf("组织"),
        "tuandui" to listOf("团队"),
        "hezuo" to listOf("合作"),
        "jingzheng" to listOf("竞争"),
        "shengchan" to listOf("生产"),
        "xiaofei" to listOf("消费"),
        "jiage" to listOf("价格"),
        "shichang" to listOf("市场"),
        "jinrong" to listOf("金融"),
        "gupiao" to listOf("股票"),
        "touzi" to listOf("投资"),
        "chengshi" to listOf("城市", "诚实"),
        "nongcun" to listOf("农村"),

        // 技术
        "jisuanji" to listOf("计算机"),
        "ruanjian" to listOf("软件"),
        "yingjian" to listOf("硬件"),
        "xitong" to listOf("系统"),
        "chengxu" to listOf("程序"),
        "sheji" to listOf("设计"),
        "kaifa" to listOf("开发"),
        "ceshi" to listOf("测试"),
        "fuwu" to listOf("服务"),
        "chanpin" to listOf("产品"),
        "kehu" to listOf("客户"),
        "yonghu" to listOf("用户"),
        "tiyan" to listOf("体验"),
        "wangzhan" to listOf("网站"),
        "youjian" to listOf("邮件"),

        // 疑问
        "shenme" to listOf("什么"),
        "nali" to listOf("哪里"),
        "nage" to listOf("哪个"),
        "duoshao" to listOf("多少"),
        "jige" to listOf("几个"),
        "shuide" to listOf("谁的"),
        "zenmeyang" to listOf("怎么样"),

        // 家庭
        "jiaren" to listOf("家人"),
        "fumu" to listOf("父母"),
        "xiongdi" to listOf("兄弟"),
        "jiemei" to listOf("姐妹"),
        "haizi" to listOf("孩子"),
        "xiansheng" to listOf("先生"),
        "nvshi" to listOf("女士"),

        // 学习
        "kecheng" to listOf("课程"),
        "keben" to listOf("课本"),
        "jiangke" to listOf("讲课"),
        "jiaoxue" to listOf("教学"),
        "kaoshi" to listOf("考试"),
        "xuexi" to listOf("学习"),
        "gongzuo" to listOf("工作"),
        "shenghuo" to listOf("生活"),

        // 方位
        "pangbian" to listOf("旁边"),
        "fujin" to listOf("附近"),
        "zhouwei" to listOf("周围"),
        "waimian" to listOf("外面"),
        "limian" to listOf("里面"),
        "shangmian" to listOf("上面"),
        "xiamian" to listOf("下面"),
        "qianmian" to listOf("前面"),
        "houmian" to listOf("后面"),
        "zuobian" to listOf("左边"),
        "youbian" to listOf("右边"),

        // 数量
        "yixie" to listOf("一些"),
        "henduo" to listOf("很多", "好多"),
        "xuduo" to listOf("许多"),
        "yiqie" to listOf("一切"),
        "quanbu" to listOf("全部"),
        "bufen" to listOf("部分"),
        "yiban" to listOf("一般"),

        // 常用短语
        "meiyou" to listOf("没有"),
        "buyong" to listOf("不用"),
        "bukeqi" to listOf("不客气"),
        "meishi" to listOf("没事"),
        "meiwenti" to listOf("没问题"),
        "meishenme" to listOf("没什么"),
        "buhaoyisi" to listOf("不好意思"),
        "duibuqi" to listOf("对不起"),
        "meiguanxi" to listOf("没关系"),

        // 更多双字词
        "nali" to listOf("哪里"),
        "nage" to listOf("那个"),
        "zhege" to listOf("这个"),
        "naxie" to listOf("那些"),
        "zhexie" to listOf("这些"),
        "zhong" to listOf("中", "种", "重"),
        "bijiao" to listOf("比较"),
        "feichang" to listOf("非常"),
        "teding" to listOf("特定"),
        "pubian" to listOf("普遍"),
        "gongtong" to listOf("共同"),
        "bici" to listOf("彼此"),
        "huxiang" to listOf("互相"),
        "xianghu" to listOf("相互"),
        "shuangfang" to listOf("双方"),
        "duifang" to listOf("对方"),
        "ziji" to listOf("自己"),
        "bieren" to listOf("别人"),
        "dajia" to listOf("大家"),
        "yiqie" to listOf("一切"),

        // 更多高频词
        "zhongyao" to listOf("重要"),
        "zhuyao" to listOf("主要"),
        "jiben" to listOf("基本"),
        "jichu" to listOf("基础"),
        "benzhi" to listOf("本质"),
        "hexin" to listOf("核心"),
        "guanjian" to listOf("关键"),
        "tiaojian" to listOf("条件"),
        "yinsu" to listOf("因素"),
        "yousu" to listOf("要素"),
        "neirong" to listOf("内容"),
        "xingshi" to listOf("形式"),
        "leixing" to listOf("类型"),
        "leibie" to listOf("类别"),
        "xiangmu" to listOf("项目"),
        "xiangxi" to listOf("详细"),
        "jianjie" to listOf("简介"),
        "gailan" to listOf("概览"),
        "zongjie" to listOf("总结"),
        "huibao" to listOf("汇报"),
        "baogao" to listOf("报告"),
        "jilu" to listOf("记录"),
        "zhengming" to listOf("证明"),
        "fanying" to listOf("反应", "反映"),
        "biaoxian" to listOf("表现"),
        "tixian" to listOf("体现"),
        "zhanshi" to listOf("展示"),
        "zhanshi" to listOf("展示"),
        "zhanshi" to listOf("展示"),
        "zhanshi" to listOf("展示"),

        // 更多常用双字组合
        "dehua" to listOf("的话"),
        "yiqi" to listOf("一起"),
        "yihou" to listOf("以后"),
        "yiqian" to listOf("以前", "一起"),
        "yuzhou" to listOf("宇宙"),
        "shijie" to listOf("世界"),
        "guojia" to listOf("国家"),
        "shehui" to listOf("社会"),
        "renlei" to listOf("人类"),
        "dongwu" to listOf("动物"),
        "zhiwu" to listOf("植物"),
        "shiwu" to listOf("食物", "事物"),
        "yinliao" to listOf("饮料"),
        "shuiguo" to listOf("水果"),
        "shucai" to listOf("蔬菜"),
        "roulei" to listOf("肉类"),
        "tiaoliao" to listOf("调料"),
        "weidao" to listOf("味道"),
        "yanse" to listOf("颜色"),
        "xingzhuang" to listOf("形状"),
        "daxiao" to listOf("大小"),
        "changdu" to listOf("长度"),
        "gaodu" to listOf("高度"),
        "kuandu" to listOf("宽度"),
        "zhongliang" to listOf("重量"),
        "mianji" to listOf("面积"),
        "tiji" to listOf("体积"),
        "rongliang" to listOf("容量"),
        "wendu" to listOf("温度"),
        "sudu" to listOf("速度"),
        "zhongliang" to listOf("重量"),
        "nengli" to listOf("能力"),
        "jineng" to listOf("技能"),
        "jineng" to listOf("技能"),
        "jishu" to listOf("技术"),
        "jiqiao" to listOf("技巧"),
        "fangfa" to listOf("方法"),
        "celue" to listOf("策略"),
        "zhanlue" to listOf("战略"),
        "mudi" to listOf("目的"),
        "mubiao" to listOf("目标"),
        "dongji" to listOf("动机"),
        "yuanyin" to listOf("原因"),
        "jieguo" to listOf("结果"),
        "guocheng" to listOf("过程"),
        "kaishi" to listOf("开始"),
        "jieshu" to listOf("结束"),
        "wancheng" to listOf("完成"),
        "shibai" to listOf("失败"),
        "chenggong" to listOf("成功"),
        "chengji" to listOf("成绩"),
        "xiaoguo" to listOf("效果"),
        "xiaolv" to listOf("效率"),
        "zhiliang" to listOf("质量"),
        "shuiping" to listOf("水平"),
        "biaozhun" to listOf("标准"),
        "guize" to listOf("规则"),
        "zhangze" to listOf("章程"),
        "tIAoli" to listOf("条例"),
        "tiaokuan" to listOf("条款"),
        "hetong" to listOf("合同"),
        "xieyi" to listOf("协议"),
        "xianding" to listOf("限定"),
        "yueshu" to listOf("约束"),
        "zunshou" to listOf("遵守"),
        "weifan" to listOf("违反"),
        "weifa" to listOf("违法"),
        "hefa" to listOf("合法"),
        "falv" to listOf("法律"),
        "fagui" to listOf("法规"),
        "tiaoli" to listOf("条例"),
        "zhidu" to listOf("制度"),
        "zhengce" to listOf("政策"),
        "cuoshi" to listOf("措施"),
        "banfa" to listOf("办法"),
        "duice" to listOf("对策"),
        "yingdui" to listOf("应对"),
        "chuli" to listOf("处理"),
        "jiejue" to listOf("解决"),
        "yingjie" to listOf("迎接"),
        "yingzhan" to listOf("迎战"),
        "bi mian" to listOf("避免"),
        "bimian" to listOf("避免"),
        "fangzhi" to listOf("防止"),
        "yufang" to listOf("预防"),
        "xiao chu" to listOf("消除"),
        "xiaochu" to listOf("消除"),
        "jianshao" to listOf("减少"),
        "zengjia" to listOf("增加"),
        "zengduo" to listOf("增多"),
        "ti gao" to listOf("提高"),
        "tigao" to listOf("提高"),
        "jiangdi" to listOf("降低"),
        "xiajiang" to listOf("下降"),
        "shangsheng" to listOf("上升"),
        "tigao" to listOf("提高"),
        "jinbu" to listOf("进步"),
        "tui bu" to listOf("退步"),
        "fa zhan" to listOf("发展"),
        "fazhan" to listOf("发展"),
        "gai ge" to listOf("改革"),
        "chuangxin" to listOf("创新"),
        "chuanbo" to listOf("传播"),
        "chuanran" to listOf("传染"),
        "gan ran" to listOf("感染"),
        "ganran" to listOf("感染"),
        "zhiliao" to listOf("治疗"),
        "yufang" to listOf("预防"),
        "jiankang" to listOf("健康"),
        "jibing" to listOf("疾病"),
        "yaowu" to listOf("药物"),
        "yaopin" to listOf("药品"),
        "yisheng" to listOf("医生"),
        "hushi" to listOf("护士"),
        "bingren" to listOf("病人"),
        "huanzhe" to listOf("患者"),
        "jianyan" to listOf("检验"),
        "jiance" to listOf("检测"),
        "zhenduan" to listOf("诊断"),
        "zhiliao" to listOf("治疗"),
        "kangfu" to listOf("康复"),
        "yimiao" to listOf("疫苗"),
        "mianyi" to listOf("免疫"),
        "bingdu" to listOf("病毒"),
        "xijun" to listOf("细菌"),
        "ganran" to listOf("感染"),

        // 更多日常用词
        "xiuxi" to listOf("休息"),
        "qichuang" to listOf("起床"),
        "shuijiao" to listOf("睡觉"),
        "chifan" to listOf("吃饭"),
        "he shui" to listOf("喝水"),
        "heshui" to listOf("喝水"),
        "maicai" to listOf("买菜"),
        "zuofan" to listOf("做饭"),
        "xiwan" to listOf("洗碗"),
        "xifu" to listOf("洗衣服"),
        "liangyi" to listOf("晾衣"),
        "shangban" to listOf("上班"),
        "xiaban" to listOf("下班"),
        "jiaban" to listOf("加班"),
        "qianjia" to listOf("请假"),
        "fangjia" to listOf("放假"),
        "luying" to listOf("录音"),
        "luxiang" to listOf("录像"),
        "paizhao" to listOf("拍照"),
        "shexiang" to listOf("摄像"),
        "shiping" to listOf("视频"),
        "yinpin" to listOf("音频"),
        "tupian" to listOf("图片"),
        "zhaopian" to listOf("照片"),
        "wenjian" to listOf("文件"),
        "wendang" to listOf("文档"),
        "biaoge" to listOf("表格"),
        "biaoge" to listOf("表格"),
        "mulu" to listOf("目录"),
        "wenjianjia" to listOf("文件夹"),
        "suolvetu" to listOf("缩略图"),
        "suolue" to listOf("缩略"),
        "suolvetu" to listOf("缩略图"),

        // 国家
        "zhongguo" to listOf("中国"),
        "meiguo" to listOf("美国"),
        "riben" to listOf("日本"),
        "hanguo" to listOf("韩国"),
        "faguo" to listOf("法国"),
        "degguo" to listOf("德国"),
        "yingguo" to listOf("英国"),
        "eluosi" to listOf("俄罗斯"),
        "yindu" to listOf("印度"),
        "baxi" to listOf("巴西"),
        "aozhou" to listOf("澳洲"),
        "jianada" to listOf("加拿大"),

        // 城市
        "beijing" to listOf("北京"),
        "shanghai" to listOf("上海"),
        "guangzhou" to listOf("广州"),
        "shenzhen" to listOf("深圳"),
        "hangzhou" to listOf("杭州"),
        "nanjing" to listOf("南京"),
        "chengdu" to listOf("成都"),
        "wuhan" to listOf("武汉"),
        "xian" to listOf("西安"),
        "tianjin" to listOf("天津"),
        "chongqing" to listOf("重庆"),
        "suzhou" to listOf("苏州"),
        "qingdao" to listOf("青岛"),
        "dalian" to listOf("大连"),
        "xiamen" to listOf("厦门"),

        // 更多常用双字词
        "yiqi" to listOf("一起"),
        "yizhi" to listOf("一致", "一直"),
        "yiban" to listOf("一般", "一半"),
        "yiding" to listOf("一定"),
        "yixie" to listOf("一些"),
        "yiyang" to listOf("一样"),
        "yiyue" to listOf("一月"),
        "liangge" to listOf("两个"),
        "sange" to listOf("三个"),
        "sige" to listOf("四个"),
        "wuge" to listOf("五个"),
        "liuge" to listOf("六个"),
        "qige" to listOf("七个"),
        "bage" to listOf("八个"),
        "jiuge" to listOf("九个"),
        "shige" to listOf("十个"),
        "diyi" to listOf("第一"),
        "dier" to listOf("第二"),
        "disan" to listOf("第三"),
        "disi" to listOf("第四"),
        "diwu" to listOf("第五"),
        "diliu" to listOf("第六"),
        "diqi" to listOf("第七"),
        "diba" to listOf("第八"),
        "dijiu" to listOf("第九"),
        "dishi" to listOf("第十"),

        // 程度副词
        "hen" to listOf("很"),
        "feichang" to listOf("非常"),
        "teding" to listOf("特定"),
        "jida" to listOf("极大"),
        "jixiao" to listOf("极小"),
        "zuida" to listOf("最大"),
        "zuixiao" to listOf("最小"),
        "gengjia" to listOf("更加"),
        "yujia" to listOf("愈加"),
        "yuelaiyue" to listOf("越来越"),
        "shaowei" to listOf("稍微"),
        "lve wei" to listOf("略微"),
        "luewei" to listOf("略微"),
        "shaoxu" to listOf("少许"),
        "yidian" to listOf("一点"),
        "yidianr" to listOf("一点儿"),

        // 否定/肯定
        "bushi" to listOf("不是"),
        "buneng" to listOf("不能"),
        "buke" to listOf("不可"),
        "buyao" to listOf("不要"),
        "buxing" to listOf("不行"),
        "bucuo" to listOf("不错"),
        "bucuo" to listOf("不错"),
        "bdui" to listOf("不对"),
        "buhaoyisi" to listOf("不好意思"),
        "meiyou" to listOf("没有"),
        "meishi" to listOf("没事"),
        "meiyou" to listOf("没有"),
        "shide" to listOf("是的"),
        "dui" to listOf("对"),
        "hao" to listOf("好"),
        "haode" to listOf("好的"),
        "haoa" to listOf("好啊"),
        "xianzai" to listOf("现在"),
        "yihou" to listOf("以后"),
        "yiqian" to listOf("以前"),
        "yiqi" to listOf("一起")
    )

    /** 合法拼音音节集合（用于拆分） */
    private val validSyllables: Set<String> = charMap.keys

    /**
     * 高频字频率表（数值越小越常用）。
     * 用于对单字候选按使用频率排序，使最常用的字排在前面，贴近主流输入法体验。
     */
    private val charFrequency: Map<String, Int> = mapOf(
        "的" to 1, "是" to 2, "了" to 3, "在" to 4, "有" to 5,
        "和" to 6, "不" to 7, "这" to 8, "我" to 9, "你" to 10,
        "他" to 11, "她" to 12, "们" to 13, "个" to 14, "上" to 15,
        "下" to 16, "中" to 17, "来" to 18, "去" to 19, "大" to 20,
        "小" to 21, "好" to 22, "多" to 23, "少" to 24, "想" to 25,
        "说" to 26, "做" to 27, "看" to 28, "知" to 29, "道" to 30,
        "时" to 31, "候" to 32, "年" to 33, "月" to 34, "日" to 35,
        "天" to 36, "地" to 37, "人" to 38, "生" to 39, "会" to 40,
        "可" to 41, "以" to 42, "要" to 43, "子" to 44, "里" to 45,
        "没" to 46, "就" to 47, "也" to 48, "都" to 49, "还" to 50,
        "而" to 51, "从" to 52, "自" to 53, "到" to 54, "把" to 55,
        "被" to 56, "让" to 57, "给" to 58, "对" to 59, "过" to 60,
        "又" to 61, "再" to 62, "已" to 63, "经" to 64, "吧" to 65,
        "呢" to 66, "啊" to 67, "吗" to 68, "嘛" to 69, "那" to 70,
        "一" to 71, "个" to 72, "为" to 73, "着" to 74, "只" to 75,
        "能" to 76, "很" to 77, "这" to 78, "着" to 79, "与" to 80,
        "和" to 81, "或" to 82, "但" to 83, "所" to 84, "因" to 85
    )

    /**
     * 模糊拼音声母互换对。
     * 覆盖主流模糊音规则：z↔zh、c↔ch、s↔sh、n↔l、f↔h。
     */
    private val fuzzyPairs: List<Pair<String, String>> = listOf(
        "z" to "zh", "zh" to "z",
        "c" to "ch", "ch" to "c",
        "s" to "sh", "sh" to "s",
        "n" to "l", "l" to "n",
        "f" to "h", "h" to "f"
    )

    /**
     * 查找候选词。
     *
     * @param input 用户输入的拼音串（如 "nihao"）
     * @return 候选列表，词语优先，然后单字
     */
    fun lookup(input: String): List<String> {
        if (input.isEmpty()) return emptyList()

        val lower = input.lowercase()
        val result = mutableListOf<String>()

        // 1. 先查词语字典（整体匹配）
        wordMap[lower]?.let { result.addAll(it) }

        // 2. 查单字字典（整体匹配），并按字频排序
        result.addAll(sortByFrequency(charMap[lower] ?: emptyList()))

        // 3. 词语前缀匹配 — 输入的拼音是某个词语拼音的前缀
        // 例如输入 "ni" → 匹配 "nihao"→"你好", "nimen"→"你们" 等
        if (lower.length >= 2) {
            val prefixMatches = mutableListOf<String>()
            for ((pinyin, words) in wordMap) {
                if (pinyin.startsWith(lower) && pinyin != lower) {
                    prefixMatches.addAll(words)
                }
            }
            result.addAll(prefixMatches.distinct().take(8))
        }

        // 4. 如果没有整体匹配，尝试拆分音节
        if (result.isEmpty() && lower.length > 2) {
            val split = splitSyllables(lower)
            if (split.isNotEmpty()) {
                // 尝试逐字匹配，每个音节取频率最高的字
                val combined = StringBuilder()
                var allMatched = true
                for (syl in split) {
                    val list = charMap[syl]
                    if (list != null && list.isNotEmpty()) {
                        combined.append(sortByFrequency(list).first())
                    } else {
                        allMatched = false
                        break
                    }
                }
                if (allMatched && combined.isNotEmpty()) {
                    result.add(combined.toString())
                }

                // 也把第一个音节的单字加进去（按字频排序）
                val firstSyl = split[0]
                val firstChars = charMap[firstSyl]
                if (firstChars != null) {
                    result.addAll(sortByFrequency(firstChars))
                }
            }
        }

        // 5. 前缀匹配（输入的拼音是某个音节的前缀）
        if (result.isEmpty()) {
            for (syl in validSyllables) {
                if (syl.startsWith(lower) && syl != lower) {
                    result.addAll(sortByFrequency(charMap[syl] ?: emptyList()))
                    if (result.size >= 6) break
                }
            }
        }

        // 6. 若仍无结果，尝试模糊拼音变体
        if (result.isEmpty()) {
            val fuzzy = fuzzyLookup(lower)
            if (fuzzy.isNotEmpty()) {
                result.addAll(fuzzy)
            }
        }

        return result.distinct().take(20)
    }

    /**
     * 基于已选汉字进行联想推荐。
     * 利用常用词组搭配关系，返回可能跟在输入汉字后面的候选字。
     *
     * @param chars 已输入的汉字（1-2个字）
     * @return 联想候选列表
     */
    fun lookupByCharacter(chars: String): List<String> {
        if (chars.isEmpty()) return emptyList()

        val last = chars.last()
        val result = mutableListOf<String>()

        // 从词语字典中查找以该字开头的词语，提取后续字
        for ((_, wordList) in wordMap) {
            for (word in wordList) {
                if (word.startsWith(last) && word.length > 1) {
                    // 提取下一个字
                    val nextChar = word.substring(1, 2)
                    if (!result.contains(nextChar)) {
                        result.add(nextChar)
                    }
                }
            }
            if (result.size >= 15) break
        }

        // 如果没有找到联想词，返回常用高频字
        if (result.isEmpty()) {
            result.addAll(listOf("的", "是", "了", "在", "有", "和", "不", "这", "我", "你", "他", "她", "们", "个", "上"))
        }

        return sortByFrequency(result).distinct().take(15)
    }

    /**
     * 拆分拼音音节。
     *
     * 采用动态规划寻找能完整覆盖输入串的最优拆分，优先长音节、音节数最少的方案；
     * 若无法完整覆盖（例如用户正在输入的半截拼音），再回退到贪心算法跳过无法匹配的字符。
     *
     * 例: "nihao"     → ["ni", "hao"]
     * 例: "zhongguo"  → ["zhong", "guo"]
     * 例: "woaixuexi" → ["wo", "ai", "xue", "xi"]
     */
    private fun splitSyllables(input: String): List<String> {
        if (input.isEmpty()) return emptyList()

        // 先尝试动态规划：寻找能完整覆盖输入串的拆分
        val dpResult = splitSyllablesDP(input)
        if (dpResult.isNotEmpty()) {
            return dpResult
        }

        // 回退到贪心算法：跳过无法匹配的字符
        val result = mutableListOf<String>()
        var pos = 0
        while (pos < input.length) {
            var found = false
            // 从最长(6)到最短(1)尝试匹配
            for (len in minOf(6, input.length - pos) downTo 1) {
                val sub = input.substring(pos, pos + len)
                if (sub in validSyllables) {
                    result.add(sub)
                    pos += len
                    found = true
                    break
                }
            }
            if (!found) {
                // 匹配失败，跳过一个字符
                pos++
            }
        }

        return result
    }

    /**
     * 动态规划拆分拼音音节。
     *
     * 记忆化搜索：dp(start) = 从位置 start 到末尾、能完整覆盖的最优音节列表（null 表示不可行）。
     * 在所有可行拆分中选择音节数最少者（等价于尽量使用长音节），符合主流输入法的拆分直觉。
     */
    private fun splitSyllablesDP(input: String): List<String> {
        val n = input.length
        if (n == 0) return emptyList()

        val memo = HashMap<Int, List<String>?>()

        fun solve(start: Int): List<String>? {
            if (start == n) return emptyList()
            memo[start]?.let { return it }

            var best: List<String>? = null
            val maxLen = minOf(6, n - start)
            for (len in maxLen downTo 1) {
                val sub = input.substring(start, start + len)
                if (sub in validSyllables) {
                    val rest = solve(start + len)
                    if (rest != null) {
                        val candidate = listOf(sub) + rest
                        val bestSize = best?.size ?: Int.MAX_VALUE
                        if (candidate.size < bestSize) {
                            best = candidate
                        }
                    }
                }
            }
            memo[start] = best
            return best
        }

        return solve(0) ?: emptyList()
    }

    /**
     * 按字频对候选字排序（频率高者靠前）。
     * 未收录在频率表中的字保持原相对顺序，排在已收录字之后。
     */
    private fun sortByFrequency(chars: List<String>): List<String> {
        if (chars.size <= 1) return chars
        // 稳定排序：保留字典原有顺序作为次要排序键
        return chars.mapIndexed { index, ch -> ch to index }
            .sortedWith(compareBy({ charFrequency[it.first] ?: Int.MAX_VALUE }, { it.second }))
            .map { it.first }
    }

    /**
     * 生成单个音节的模糊拼音变体（含原音节）。
     * 仅替换声母部分（z/zh、c/ch、s/sh、n/l、f/h），韵母保持不变。
     */
    private fun fuzzySyllableVariants(syllable: String): List<String> {
        val variants = mutableListOf(syllable)
        for ((from, to) in fuzzyPairs) {
            if (syllable.startsWith(from)) {
                val swapped = to + syllable.substring(from.length)
                if (swapped != syllable && swapped !in variants) {
                    variants.add(swapped)
                }
            }
        }
        return variants
    }

    /**
     * 生成整串拼音的模糊变体（含原串）。
     * 用于词语整体匹配：替换首音节的声母即可覆盖大多数模糊音场景。
     */
    private fun fuzzyStringVariants(input: String): List<String> {
        val variants = mutableListOf(input)
        for ((from, to) in fuzzyPairs) {
            if (input.startsWith(from)) {
                val swapped = to + input.substring(from.length)
                if (swapped != input && swapped !in variants) {
                    variants.add(swapped)
                }
            }
        }
        return variants
    }

    /**
     * 模糊拼音查找。
     *
     * 尝试常见的模糊拼音变体（z↔zh、c↔ch、s↔sh、n↔l、f↔h）：
     * 1. 对整串做声母替换后查词语字典；
     * 2. 拆分音节后，对每个音节做模糊变体并查单字字典。
     *
     * @param input 用户输入的拼音串
     * @return 模糊匹配到的候选列表
     */
    fun fuzzyLookup(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        val lower = input.lowercase()
        val result = mutableListOf<String>()

        // 1. 整体模糊变体（用于词语匹配）
        for (variant in fuzzyStringVariants(lower)) {
            wordMap[variant]?.let { result.addAll(it) }
        }

        // 2. 按音节拆分后，对每个音节尝试模糊变体并匹配单字
        val syllables = splitSyllables(lower)
        for (syl in syllables) {
            for (variant in fuzzySyllableVariants(syl)) {
                charMap[variant]?.let { result.addAll(sortByFrequency(it)) }
            }
        }

        return result.distinct().take(20)
    }

    /**
     * 句子级拼音转换。
     *
     * 将完整的连续拼音串拆分为音节，再贪心地用最长词语覆盖、剩余音节用单字补全，
     * 组合成最可能的句子。
     *
     * 例: "woaixuexi" → ["wo","ai","xue","xi"] → "我爱学习"（"xuexi" 命中词语"学习"）
     *
     * @param pinyin 完整拼音串（无分隔符）
     * @return 最佳匹配的句子；若无法匹配返回空字符串
     */
    fun lookupSentence(pinyin: String): String {
        if (pinyin.isEmpty()) return ""
        val lower = pinyin.lowercase()

        // 先尝试整体词语匹配
        wordMap[lower]?.firstOrNull()?.let { return it }

        val syllables = splitSyllables(lower)
        if (syllables.isEmpty()) return ""

        val sb = StringBuilder()
        var i = 0
        while (i < syllables.size) {
            var matched = false
            // 尝试从当前位置开始用最长词语覆盖（最多取 6 个音节）
            val maxWordLen = minOf(6, syllables.size - i)
            for (wlen in maxWordLen downTo 2) {
                val combined = syllables.subList(i, i + wlen).joinToString("")
                val word = wordMap[combined]?.firstOrNull()
                if (word != null) {
                    sb.append(word)
                    i += wlen
                    matched = true
                    break
                }
            }
            if (!matched) {
                // 剩余音节用单字补全，取该音节下频率最高的字
                val syl = syllables[i]
                val chars = charMap[syl]
                if (chars != null && chars.isNotEmpty()) {
                    sb.append(sortByFrequency(chars).first())
                }
                i++
            }
        }
        return sb.toString()
    }

    /**
     * 判断输入是否是合法拼音的前缀。
     * 用于决定是否显示候选词栏。
     */
    fun isPinyinPrefix(input: String): Boolean {
        if (input.isEmpty()) return false
        val lower = input.lowercase()
        // 完全匹配
        if (lower in validSyllables) return true
        // 词语前缀
        if (wordMap.keys.any { it.startsWith(lower) }) return true
        // 音节前缀
        if (validSyllables.any { it.startsWith(lower) }) return true
        // 可以拆分
        if (lower.length > 2 && splitSyllables(lower).isNotEmpty()) return true
        return false
    }
}
