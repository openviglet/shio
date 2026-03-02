package com.viglet.shio.website;

import com.google.common.base.Stopwatch;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.utils.ShPostUtils;
import com.viglet.shio.website.cache.component.ShCacheJavascript;
import com.viglet.shio.website.cache.component.ShCachePreviewHtml;
import com.viglet.shio.website.component.ShSitesPageLayout;
import com.viglet.shio.website.nashorn.ShNashornEngineProcess;
import com.viglet.shio.website.utils.ShSitesPostUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ShSitesRegion {
    private final ShPostRepository shPostRepository;
    private final ShPostUtils shPostUtils;
    private final ShSitesPostUtils shSitesPostUtils;
    private final ShCacheJavascript shCacheJavascript;
    private final ShNashornEngineProcess shNashornEngineProcess;
    private final ShCachePreviewHtml shCachePreviewHtml;
    private final ShSitesRegion shSitesRegionInner;

    public ShSitesRegion(ShPostRepository shPostRepository, ShPostUtils shPostUtils, ShSitesPostUtils shSitesPostUtils,
            ShCacheJavascript shCacheJavascript, ShNashornEngineProcess shNashornEngineProcess,
            ShCachePreviewHtml shCachePreviewHtml, @Lazy ShSitesRegion shSitesRegion) {
        this.shPostRepository = shPostRepository;
        this.shPostUtils = shPostUtils;
        this.shSitesPostUtils = shSitesPostUtils;
        this.shCacheJavascript = shCacheJavascript;
        this.shNashornEngineProcess = shNashornEngineProcess;
        this.shCachePreviewHtml = shCachePreviewHtml;
        this.shSitesRegionInner = shSitesRegion;
    }

    public Document shRegionFactory(ShSitesPageLayout shSitesPageLayout, String regionResult, ShSite shSite,
                                    String mimeType, HttpServletRequest request) {
        StringBuilder shObjectJS = shCacheJavascript.shObjectJSFactory();
        Document doc = null;
        if (shSitesPageLayout.getPageCacheKey().endsWith(".json") || mimeType.equals("json") || mimeType.equals("xml"))
            doc = Jsoup.parse(regionResult, StringUtils.EMPTY, Parser.xmlParser());
        else
            doc = Jsoup.parse(regionResult);

        for (Element element : doc.getElementsByAttribute("sh-region")) {
            String cachedRegion = null;
            String regionName = element.attr("sh-region");
            if (shSitesRegionInner.isCached(regionName, shSite.getId()))
                cachedRegion = shSitesRegionInner.templateScopeCache(regionName, shSitesPageLayout, shSite, shObjectJS,
                        mimeType, request);
            else
                cachedRegion = this.regionProcess(regionName, shSitesPageLayout, shSite, mimeType, request);

            if (cachedRegion != null)
                element.html(cachedRegion).unwrap();
            else {
                element.html("<div> Region Error </div>").unwrap();
                log.error("Region Error");
            }
        }
        return doc;
    }

    public String regionProcess(String regionName, ShSitesPageLayout shSitesPageLayout, ShSite shSite, String mimeType,
                                HttpServletRequest request) {
        ShPost shRegion = getRegion(regionName, shSite.getId());
        if (shRegion != null) {
            Stopwatch stopwatch = Stopwatch.createStarted();
            Map<String, ShPostAttr> shRegionPostMap = shSitesPostUtils.postToMap(shRegion);

            String shRegionJS = shRegionPostMap.get(ShSystemPostTypeAttr.JAVASCRIPT).getStrValue();

            String shRegionHTML = shRegionPostMap.get(ShSystemPostTypeAttr.HTML).getStrValue();

            Object regionResultChild = shNashornEngineProcess.render(regionName, shRegionJS, shRegionHTML, request,
                    shSitesPageLayout.getShContent());

            String regionHTML = this
                    .shRegionFactory(shSitesPageLayout, regionResultChild.toString(), shSite, mimeType, request).html();

            stopwatch.stop();

            long timeProcess = stopwatch.elapsed(TimeUnit.MILLISECONDS);

            Comment comment = new Comment(String.format(" sh-region: %s, id: %s, processed in: %s ms ", regionName,
                    shRegion.getId(), timeProcess));
            String previewRegion = shCachePreviewHtml.shPreviewRegionFactory();

            return previewRegion.replace("{{content}}", String.format("%s%s", comment, regionHTML));

        }
        return null;

    }

    public ShPost getRegion(String regionName, String siteId) {
        List<ShPost> shRegionPosts = shPostRepository.findByTitle(regionName);
        ShPost shRegion = null;
        if (shRegionPosts != null) {
            for (ShPost shRegionPost : shRegionPosts) {
                if (shPostUtils.getSite(shRegionPost).getId().equals(siteId))
                    shRegion = shRegionPost;
            }

        }
        return shRegion;
    }

    @Cacheable(value = "region", key = "{#root.methodName, #regionName, #shSite.getId()}", sync = true)
    public String templateScopeCache(String regionName, ShSitesPageLayout shSitesPageLayout, ShSite shSite,
                                     StringBuilder shObjectJS, String mimeType, HttpServletRequest request) {
        return regionProcess(regionName, shSitesPageLayout, shSite, mimeType,
                request);
    }

    @Cacheable(value = "region", key = "{#root.methodName, #regionName, #siteId}", sync = true)
    public boolean isCached(String regionName, String siteId) {
        ShPost shRegion = getRegion(regionName, siteId);
        if (shRegion != null) {
            Map<String, ShPostAttr> shRegionPostMap = shSitesPostUtils.postToMap(shRegion);
            if (shRegionPostMap.get(ShSystemPostTypeAttr.CACHED) != null && shRegionPostMap.get(ShSystemPostTypeAttr.CACHED).getStrValue() != null) {
                if (log.isDebugEnabled())
                    log.debug("Region must be cached");
                return shRegionPostMap.get(ShSystemPostTypeAttr.CACHED).getStrValue().equals("yes");
            }
        }
        if (log.isDebugEnabled())
            log.debug("Region should not be cached");

        return false;

    }
}
