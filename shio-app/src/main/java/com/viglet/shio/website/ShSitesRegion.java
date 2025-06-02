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
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ShSitesRegion {
    static final Logger logger = LogManager.getLogger(ShSitesRegion.class);
    @Autowired
    private ShPostRepository shPostRepository;
    @Autowired
    private ShPostUtils shPostUtils;
    @Autowired
    private ShSitesPostUtils shSitesPostUtils;
    @Autowired
    private ShCacheJavascript shCacheJavascript;
    @Autowired
    private ShNashornEngineProcess shNashornEngineProcess;
    @Autowired
    private ShCachePreviewHtml shCachePreviewHtml;

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
            if (isCached(regionName, shSite.getId()))
                cachedRegion = templateScopeCache(regionName, shSitesPageLayout, shSite, shObjectJS,
                        mimeType, request);
            else
                cachedRegion = this.regionProcess(regionName, shSitesPageLayout, shSite, mimeType, request);

            if (cachedRegion != null)
                element.html(cachedRegion).unwrap();
            else {
                element.html("<div> Region Error </div>").unwrap();
                logger.error("Region Error");
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
                if (logger.isDebugEnabled())
                    logger.debug("Region must be cached");
                return shRegionPostMap.get(ShSystemPostTypeAttr.CACHED).getStrValue().equals("yes");
            }
        }
        if (logger.isDebugEnabled())
            logger.debug("Region should not be cached");

        return false;

    }
}
