package com.viglet.shio.website;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.utils.ShFolderUtils;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ShSitesDetectContextURL {
    private final ShSiteRepository shSiteRepository;
    private final ShSitesContextComponent shSitesContextComponent;
    private final ShFolderUtils shFolderUtils;
    private static final String SEPARATOR = "/";

    public ShSitesDetectContextURL(ShSiteRepository shSiteRepository, ShSitesContextComponent shSitesContextComponent, ShFolderUtils shFolderUtils) {
        this.shSiteRepository = shSiteRepository;
        this.shSitesContextComponent = shSitesContextComponent;
        this.shFolderUtils = shFolderUtils;
    }

    public void detectContextURL(String url, ShSitesContextURL shSitesContextURL) {
        shSitesContextURL.getInfo().setContextURL(url);
        String shSiteName = null;
        String[] contexts = url.split(SEPARATOR);
        for (int i = 1; i < contexts.length; i++) {
            switch (i) {
                case 1:
                    shSitesContextURL.getInfo().setShContext(contexts[i]);
                    break;
                case 2:
                    shSiteName = contexts[i];
                    break;
                case 3:
                    shSitesContextURL.getInfo().setShFormat(contexts[i]);
                    break;
                case 4:
                    shSitesContextURL.getInfo().setShLocale(contexts[i]);
                    break;
                default:
                    break;
            }
        }

        ShSite shSite = shSiteRepository.findByFurl(shSiteName);
        if (shSite == null) {
            shSite = shSiteRepository.findByName(shSiteName);
        }
        if (shSite == null) {
            log.warn("No site found for context: " + shSiteName);
            return;
        }
        shSitesContextURL.getInfo().setSiteId(shSite.getId());

        List<String> contentPath = shSitesContextComponent
                .contentPathFactory(shSitesContextURL.getInfo().getContextURL());

        String objectName = shSitesContextComponent.objectNameFactory(contentPath);

        ShFolder shFolder = shFolderUtils.folderFromPath(shSite,
                shSitesContextComponent.folderPathFactory(contentPath));
        if (shFolder != null) {
            shSitesContextURL.getInfo().setParentFolderId(shFolder.getId());
        } else {
            log.info("No folder for " + shSitesContextURL.getInfo().getContextURL());
        }

        ShObjectImpl shObject = shSitesContextComponent.shObjectItemFactory(shSite, shFolder, objectName);
        if (shObject != null) {
            shSitesContextURL.getInfo().setObjectId(shObject.getId());
        }

    }


    public void detectContextURL(ShSitesContextURL shSitesContextURL) {

        detectContextURL(shSitesContextURL.getInfo().getContextURL(), shSitesContextURL);
    }


}
