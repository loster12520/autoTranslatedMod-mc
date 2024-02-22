import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.youdao.aicloud.translate.utils.AuthV3Util
import com.youdao.aicloud.translate.utils.HttpUtil
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.PrintWriter
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Scanner
import java.util.jar.JarInputStream


fun main(args: Array<String>) {
    val config=getConfig(args[0])
    val modLanguageList=modRead(argsDispose(args))
    val translatedModLanguageList=fileTranslate(modLanguageList,config.APP_KEY,config.APP_SECRET)
    pack(translatedModLanguageList, config.basePath, packMcMetaEdit(getPackMcMeta(File(args[1])),"Machine translation by youdao"))
}

fun modRead(fileList: List<File>):List<Pair<String,String>>{
    val res= mutableListOf<Pair<String,String>>()
    fileList.forEach{file ->
        val jarInputStream=JarInputStream(FileInputStream(file))
        while (true){
            val jarEntry= jarInputStream.nextJarEntry ?: break
            if (jarEntry.name.matches("assets/[A-za-z]+/lang/en_us.json".toRegex())){
                val scanner=Scanner(jarInputStream)
                val text=StringBuffer()
                while (scanner.hasNext()){
                    text.append(scanner.nextLine())
                }
                val name=jarEntry.name.substring(7,jarEntry.name.length-16)
                res.add(Pair(text.toString(),name))
                println("文件${file.name}获取成功")
                break
            }
        }
    }
    println("文本获取成功")
    return res
}
fun enToZh(text:String,APP_KEY:String,APP_SECRET:String):String{
    // 添加请求参数
    val params: Map<String, Array<String>> = createRequestParams(text)
    // 添加鉴权相关参数
    AuthV3Util.addAuthParams(APP_KEY, APP_SECRET, params)
    // 请求api服务
    val result = HttpUtil.doPost("https://openapi.youdao.com/api", null, params, "application/json")
    // 处理结果
    if(JSONObject(String(result)).getInt("errorCode")==411){
        Thread.sleep(10)
        return enToZh(text,APP_KEY,APP_SECRET)
    }
    val res = JSONObject(String(result))
        .getJSONArray("translation")
        .getString(0)
    // 返回结果
    println("翻译成功：${text}->${res}")
    return res
}
private fun createRequestParams(text: String): HashMap<String, Array<String>> {
    /*
         * note: 将下列变量替换为需要请求的参数
         * 取值参考文档: https://ai.youdao.com/DOCSIRMA/html/%E8%87%AA%E7%84%B6%E8%AF%AD%E8%A8%80%E7%BF%BB%E8%AF%91/API%E6%96%87%E6%A1%A3/%E6%96%87%E6%9C%AC%E7%BF%BB%E8%AF%91%E6%9C%8D%E5%8A%A1/%E6%96%87%E6%9C%AC%E7%BF%BB%E8%AF%91%E6%9C%8D%E5%8A%A1-API%E6%96%87%E6%A1%A3.html
         */
    val q = text
    val from = "en"
    val to = "zh"
    return object : HashMap<String, Array<String>>() {
        init {
            put("q", arrayOf(q))
            put("from", arrayOf(from))
            put("to", arrayOf(to))
        }
    }
}
fun fileTranslate(list:List<Pair<String,String>>, APP_KEY: String,APP_SECRET: String): List<Pair<String,String>>{
    val res= mutableListOf<Pair<String,String>>()
    list.forEach {
        val jsonElement=JsonParser.parseString(it.first)
        val jsonObject=jsonElement.asJsonObject
        jsonObject.entrySet().forEach {
            jsonObject.addProperty(it.key,enToZh(it.value.asString,APP_KEY,APP_SECRET))
        }
        res.add(Pair(GsonBuilder().setPrettyPrinting().create().toJson(jsonObject),it.second))
    }
    println("文本翻译成功")
    return res
}
fun getPackMcMeta(file:File):String{
    val jarInputStream=JarInputStream(FileInputStream(file))
    while (true){
        val jarEntry=jarInputStream.nextJarEntry ?: break
        if(jarEntry.name.equals("pack.mcmeta")){
            val scanner=Scanner(jarInputStream)
            val text=StringBuffer()
            while (scanner.hasNext()){
                text.append(scanner.nextLine())
            }
            println("mcmeta文件获取成功")
            return text.toString()
        }
    }
    throw RuntimeException("it is not a mod jar file")
}
fun packMcMetaEdit(json:String,description:String):String{
    val jsonElement=JsonParser.parseString(json)
    val jsonObject=jsonElement.asJsonObject
    jsonObject.getAsJsonObject("pack")
        .addProperty("description",description)
    println("mcmeta文件转换成功")
    return jsonObject.toString()
}
fun pack(list:List<Pair<String,String>>, basePath:String, packMcMeta:String){
    Files.createDirectories(Paths.get(basePath,"output"))
    if (!File(basePath+"output/"+"pack.mcmeta").exists())
        File(basePath+"output/"+"pack.mcmeta").createNewFile()
    val packMcMetaPrintWriter=PrintWriter(File(basePath+"output/"+"pack.mcmeta"))
    packMcMetaPrintWriter.println(packMcMeta)
    packMcMetaPrintWriter.buffered().close()
    list.forEach {
        val path= Paths.get(basePath,"output/","assets",it.second,"lang")
        Files.createDirectories(path)
        val printWriter=PrintWriter(File("${basePath}output/assets/${it.second}/lang/zh_cn.json"))
        printWriter.println(it.first)
        printWriter.buffered().close()
    }
    println("打包成功")
}
data class Config(
    val APP_KEY: String,
    val APP_SECRET: String,
    val basePath: String,
    val description: String
)
fun getConfig(path:String):Config{
    val gson= Gson()
    val scanner=Scanner(File(path))
    val stringBuffer=StringBuffer()
    while (scanner.hasNext())
        stringBuffer.append(scanner.nextLine()+"\n")
    return gson.fromJson(stringBuffer.toString(),Config::class.java)
}
fun argsDispose(args: Array<String>):List<File>{
    val fileList= mutableListOf<File>()
    args.forEach {
        if (File(it).exists())
            fileList.add(File(it))
        else
            println("$it 文件不存在")
    }
    return fileList.drop(1)
}