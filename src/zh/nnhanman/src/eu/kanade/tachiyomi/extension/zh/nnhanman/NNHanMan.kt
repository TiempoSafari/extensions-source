package eu.kanade.tachiyomi.extension.zh.nnhanman

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

class NNHanMan : HttpSource() {

    override val name = "鸟鸟韩漫"
    override val baseUrl = "https://nnhanman.xyz"
    override val lang = "zh"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    // ==================== Popular ====================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/comics/all/ob/hits/st/all/page/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ==================== Latest ====================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/comics/all/ob/time/st/all/page/$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    // ==================== Search ====================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()
            return GET(url, headers)
        }

        var category = "all"
        var orderBy = "time"
        var status = "all"

        for (filter in filters) {
            when (filter) {
                is CategoryFilter -> category = filter.values[filter.state].value
                is OrderFilter -> orderBy = filter.values[filter.state].value
                is StatusFilter -> status = filter.values[filter.state].value
                else -> {}
            }
        }

        return GET("$baseUrl/comics/$category/ob/$orderBy/st/$status/page/$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ==================== Manga Details ====================

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = Jsoup.parse(response.body.string())
        return SManga.create().apply {
            title = doc.selectFirst("h1")?.text()?.trim() ?: ""
            thumbnail_url = doc.selectFirst(".book-cover img, .comic-cover img, img[alt]")
                ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            author = doc.selectFirst(".comic-author, .author")?.text()?.trim()
            genre = doc.select(".tag a, .comic-tags a").joinToString(", ") { it.text().trim() }
            description = doc.selectFirst(".comic-intro, .intro, .description, p.desc")?.text()?.trim()
            status = when {
                doc.text().contains("连载中") -> SManga.ONGOING
                doc.text().contains("已完结") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ==================== Chapter List ====================

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body.string())
        val chapterLinks = doc.select("ul.chapter-list a, .chapter-list li a, ul li a[href*='/chapter-']")
        return chapterLinks.map { a ->
            SChapter.create().apply {
                setUrlWithoutDomain(a.attr("href"))
                name = a.text().trim()
            }
        }
    }

    // ==================== Page List ====================

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val doc = Jsoup.parse(response.body.string())
        val imgs = doc.select("table img, .chapter-content img, #chapter-content img, img[data-src], img[data-original]")
        return imgs.mapIndexed { index, img ->
            val url = img.attr("data-src").ifBlank {
                img.attr("data-original").ifBlank {
                    img.attr("src")
                }
            }
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ==================== Helpers ====================

    private fun parseMangaList(response: Response): MangasPage {
        val doc = Jsoup.parse(response.body.string())
        val items = doc.select("ul.comic-list li, .book-list li, ul li:has(a[href*='/comic/'])")
        val mangas = items.mapNotNull { li ->
            val a = li.selectFirst("a[href*='/comic/']") ?: return@mapNotNull null
            val img = li.selectFirst("img")
            SManga.create().apply {
                setUrlWithoutDomain(a.attr("href"))
                title = (a.attr("title").ifBlank { img?.attr("alt") }).ifBlank {
                    li.selectFirst("p, span, .title")?.text() ?: ""
                }.trim()
                thumbnail_url = img?.let {
                    it.attr("data-src").ifBlank { it.attr("src") }
                }
            }
        }
        val hasNext = doc.selectFirst("a:contains(下一页), a.next") != null
        return MangasPage(mangas, hasNext)
    }

    // ==================== Filters ====================

    override fun getFilterList() = FilterList(
        Filter.Header("搜索时分类筛选无效"),
        CategoryFilter(),
        OrderFilter(),
        StatusFilter(),
    )

    class CategoryFilter : Filter.Select<CategoryOption>(
        "分类",
        arrayOf(
            CategoryOption("全部", "all"),
            CategoryOption("正妹", "正妹"),
            CategoryOption("恋爱", "恋爱"),
            CategoryOption("出版漫画", "出版漫画"),
            CategoryOption("肉慾", "肉慾"),
            CategoryOption("浪漫", "浪漫"),
            CategoryOption("大尺度", "大尺度"),
            CategoryOption("巨乳", "巨乳"),
            CategoryOption("有夫之婦", "有夫之婦"),
            CategoryOption("女大生", "女大生"),
            CategoryOption("狗血劇", "狗血劇"),
            CategoryOption("同居", "同居"),
            CategoryOption("好友", "好友"),
            CategoryOption("調教", "調教"),
            CategoryOption("动作", "动作"),
            CategoryOption("後宮", "後宮"),
            CategoryOption("不倫", "不倫"),
            CategoryOption("3D", "3D"),
            CategoryOption("校園", "校園"),
            CategoryOption("耽美", "耽美"),
            CategoryOption("日漫", "日漫"),
        ),
    )

    class OrderFilter : Filter.Select<OrderOption>(
        "排序",
        arrayOf(
            OrderOption("按时间", "time"),
            OrderOption("按热度", "hits"),
        ),
    )

    class StatusFilter : Filter.Select<StatusOption>(
        "状态",
        arrayOf(
            StatusOption("全部", "all"),
            StatusOption("已完结", "completed"),
            StatusOption("连载中", "serialized"),
        ),
    )
}

data class CategoryOption(val name: String, val value: String) {
    override fun toString() = name
}

data class OrderOption(val name: String, val value: String) {
    override fun toString() = name
}

data class StatusOption(val name: String, val value: String) {
    override fun toString() = name
}