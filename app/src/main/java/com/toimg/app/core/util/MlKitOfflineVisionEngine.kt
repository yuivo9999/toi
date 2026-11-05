package com.toimg.app.core.util

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.domain.model.RecognitionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Google ML Kit 本地端侧图像识别引擎。
 * 完全在手机本地运行，无需 API Key，零网络流量消耗，保护隐私，作为大模型云端失败或无网时的兜底方案。
 */
object MlKitOfflineVisionEngine {

    private val labeler by lazy {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.40f)
            .build()
        ImageLabeling.getClient(options)
    }

    // 标签中英文语义映射表
    private val labelTranslationMap = mapOf(
        // 动物/宠物
        "cat" to listOf("猫咪", "宠物"),
        "kitten" to listOf("猫咪", "宠物"),
        "felidae" to listOf("猫咪", "宠物"),
        "dog" to listOf("狗狗", "宠物"),
        "puppy" to listOf("狗狗", "宠物"),
        "canidae" to listOf("狗狗", "宠物"),
        "pet" to listOf("宠物"),
        "animal" to listOf("动物"),
        "mammal" to listOf("动物"),
        "bird" to listOf("鸟类", "自然"),
        "fish" to listOf("水族", "动物"),

        // 美食/餐饮
        "food" to listOf("美食", "餐饮"),
        "dish" to listOf("美食", "佳肴"),
        "cuisine" to listOf("美食", "餐饮"),
        "tableware" to listOf("餐具", "餐饮"),
        "plate" to listOf("餐盘", "美食"),
        "meal" to listOf("美食", "正餐"),
        "meat" to listOf("美食", "肉食"),
        "fruit" to listOf("水果", "生鲜"),
        "dessert" to listOf("甜品", "下午茶"),
        "vegetable" to listOf("蔬菜", "轻食"),
        "drink" to listOf("饮品", "咖啡茶饮"),
        "coffee" to listOf("咖啡", "饮品"),
        "baked goods" to listOf("烘焙", "面包点心"),
        "fast food" to listOf("快餐", "小吃"),

        // 自然/风光
        "nature" to listOf("自然风景", "风光"),
        "landscape" to listOf("风景", "户外"),
        "sky" to listOf("天空", "自然"),
        "cloud" to listOf("白云", "天气"),
        "tree" to listOf("树木", "自然"),
        "plant" to listOf("植物", "绿植"),
        "mountain" to listOf("山川", "风景"),
        "water" to listOf("水景", "自然"),
        "sea" to listOf("海洋", "沙滩"),
        "ocean" to listOf("海洋", "风光"),
        "sunset" to listOf("日落晚霞", "风光"),
        "sunrise" to listOf("日出晨曦", "风光"),
        "flower" to listOf("花卉", "植物"),
        "beach" to listOf("沙滩", "海滨"),

        // 人物/日常
        "person" to listOf("人物合照", "日常"),
        "people" to listOf("合照人群", "日常"),
        "smile" to listOf("人物特写", "笑颜"),
        "face" to listOf("肖像面容", "人物"),
        "selfie" to listOf("自拍", "生活日常"),
        "child" to listOf("儿童亲子", "生活"),
        "baby" to listOf("母婴宝贝", "亲子"),
        "woman" to listOf("人物", "人像"),
        "man" to listOf("人物", "人像"),
        "youth" to listOf("青春", "人像"),

        // 文档/票据/办公
        "document" to listOf("文档票据", "办公资料"),
        "text" to listOf("文字排版", "文档"),
        "receipt" to listOf("发票收据", "账单报销"),
        "invoice" to listOf("发票", "财务票据"),
        "paper" to listOf("纸质文件", "文档"),
        "book" to listOf("书籍阅读", "知识"),
        "newspaper" to listOf("报刊杂志", "资讯"),
        "ticket" to listOf("门票凭据", "出行"),
        "font" to listOf("字体排版", "文字"),
        "handwriting" to listOf("手写笔记", "记录"),

        // 数码/科技/截图
        "screenshot" to listOf("屏幕截图", "数码"),
        "screen" to listOf("显示屏", "数码设备"),
        "display" to listOf("屏幕画面", "电子设备"),
        "computer" to listOf("电脑数码", "办公"),
        "laptop" to listOf("笔记本电脑", "数码"),
        "electronic device" to listOf("电子设备", "数码"),
        "smartphone" to listOf("手机设备", "科技"),
        "gadget" to listOf("数码小件", "科技"),
        "software" to listOf("软件界面", "数码截图"),

        // 交通/出行
        "car" to listOf("汽车", "出行交通"),
        "vehicle" to listOf("车辆", "交通"),
        "automobile" to listOf("汽车车辆", "出行"),
        "motorcycle" to listOf("摩托车", "交通"),
        "bicycle" to listOf("自行车", "骑行"),
        "transport" to listOf("交通出行"),
        "airplane" to listOf("飞机航班", "旅行"),
        "train" to listOf("列车高铁", "出行"),

        // 建筑/室内
        "architecture" to listOf("建筑风貌", "城市"),
        "building" to listOf("楼宇建筑", "城市空间"),
        "house" to listOf("房屋住宅", "空间"),
        "room" to listOf("室内空间", "家居"),
        "interior design" to listOf("室内装潢", "家居空间"),
        "window" to listOf("门窗光影", "室内"),
        "city" to listOf("城市街景", "都市"),

        // 艺术/插画
        "art" to listOf("艺术创作", "美学"),
        "drawing" to listOf("手绘素描", "插画"),
        "painting" to listOf("绘画作品", "艺术"),
        "illustration" to listOf("卡通插画", "设计"),
        "animation" to listOf("动漫二次元", "插画"),
        "sketch" to listOf("草图手绘", "设计"),

        // 时尚/穿搭
        "clothing" to listOf("服饰穿搭", "时尚"),
        "dress" to listOf("服饰裙装", "穿搭"),
        "shoe" to listOf("鞋履", "穿搭"),
        "footwear" to listOf("鞋靴", "穿搭"),
        "fashion" to listOf("时尚穿搭", "造型")
    )

    /**
     * 对单张图片执行本地 ML Kit 图像识别分类
     */
    suspend fun recognizeImage(
        context: Context,
        image: ImageEntity
    ): RecognitionResult = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(image.uriString)
            val inputImage = try {
                InputImage.fromFilePath(context, uri)
            } catch (e: Exception) {
                // 如果直接通过 URI 路径读取受限，尝试通过 Bitmap 解码
                val bmp = ImageCompressUtil.decodeSampledBitmap(context, uri, 1024)
                if (bmp != null) InputImage.fromBitmap(bmp, 0) else null
            }


            if (inputImage == null) {
                return@withContext RecognitionResult(
                    imageId = image.id,
                    keywords = listOf("未分类图片"),
                    description = "本地读取失败：无法解码图像内容",
                    modelName = "MLKit-OnDevice",
                    isSuccess = false,
                    errorMessage = "无法从 URI 解码图片"
                )
            }

            // 调用 ML Kit 端侧识别器
            val task = labeler.process(inputImage)
            val labels = Tasks.await(task, 12, TimeUnit.SECONDS)

            if (labels.isNullOrEmpty()) {
                return@withContext RecognitionResult(
                    imageId = image.id,
                    keywords = listOf("普通照片", "未归类"),
                    description = "[本地离线] 未识别出明显分类特征",
                    modelName = "MLKit-OnDevice",
                    isSuccess = true,
                    recognizedAt = System.currentTimeMillis()
                )
            }

            val keywordSet = linkedSetOf<String>()
            val rawEnLabels = mutableListOf<String>()
            var maxConfidence = 0.0f
            var isSensitive = false
            var isDocument = false

            for (lbl in labels) {
                if (lbl.confidence > maxConfidence) {
                    maxConfidence = lbl.confidence
                }
                val rawText = lbl.text.trim()
                rawEnLabels.add(rawText)
                val lower = rawText.lowercase(Locale.ROOT)

                // 敏感凭证特征判断
                if (lower.contains("receipt") || lower.contains("invoice") || lower.contains("passport") || lower.contains("identity")) {
                    isSensitive = true
                }
                if (lower.contains("document") || lower.contains("text") || lower.contains("paper")) {
                    isDocument = true
                }

                // 尝试字典匹配
                val matched = labelTranslationMap[lower]
                if (matched != null) {
                    keywordSet.addAll(matched)
                } else {
                    // 若无精准匹配，保留原英文词
                    keywordSet.add(rawText)
                }
            }

            val topLabels = keywordSet.take(8).toList()
            val confPct = (maxConfidence * 100).toInt()
            val desc = "[本地离线识别] 识别出 ${topLabels.take(3).joinToString("、")} 等视觉特征 (置信度最高 ${confPct}%)"

            RecognitionResult(
                imageId = image.id,
                keywords = if (topLabels.isNotEmpty()) topLabels else listOf("本地相册照片"),
                description = desc,
                modelName = "MLKit-OnDevice",
                isSuccess = true,
                ocrText = if (isDocument) "包含文档/票据版面特征" else null,
                isSensitive = isSensitive,
                recognizedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            RecognitionResult(
                imageId = image.id,
                keywords = emptyList(),
                description = "本地识别异常: ${e.localizedMessage ?: "模型处理失败"}",
                modelName = "MLKit-OnDevice",
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "ML Kit 处理异常"
            )
        }
    }

    /**
     * 批量本地执行
     */
    suspend fun processBatch(
        context: Context,
        images: List<ImageEntity>
    ): List<RecognitionResult> = withContext(Dispatchers.Default) {
        images.map { img ->
            recognizeImage(context, img)
        }
    }
}
