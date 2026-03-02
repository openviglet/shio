package com.viglet.shio.utils;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.reference.ShReference;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostAttrRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.reference.ShReferenceRepository;
import com.viglet.shio.turing.ShTuringIntegration;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class ShIntegrationUtils {

    private final ShFolderRepository shFolderRepository;
    private final ShPostRepository shPostRepository;
    private final ShPostAttrRepository shPostAttrRepository;
    private final ShReferenceRepository shReferenceRepository;
    private final ShTuringIntegration shTuringIntegration;
    private final ShIntegrationUtils shIntegrationUtilsInner;

    public ShIntegrationUtils(ShFolderRepository shFolderRepository, ShPostRepository shPostRepository,
            ShPostAttrRepository shPostAttrRepository, ShReferenceRepository shReferenceRepository,
            ShTuringIntegration shTuringIntegration, @Lazy ShIntegrationUtils shIntegrationUtils) {
        this.shFolderRepository = shFolderRepository;
        this.shPostRepository = shPostRepository;
        this.shPostAttrRepository = shPostAttrRepository;
        this.shReferenceRepository = shReferenceRepository;
        this.shTuringIntegration = shTuringIntegration;
        this.shIntegrationUtilsInner = shIntegrationUtils;
    }

    @Transactional
    public boolean deleteFolder(ShFolder shFolder) throws IOException {
        shTuringIntegration.deindexObject(shFolder);

        for (ShPost shPost : shPostRepository.findByShFolder(shFolder)) {
            List<ShReference> shGlobalFromId = shReferenceRepository.findByShObjectFrom(shPost);
            List<ShReference> shGlobalToId = shReferenceRepository.findByShObjectTo(shPost);
            shReferenceRepository.deleteAllInBatch(shGlobalFromId);
            shReferenceRepository.deleteAllInBatch(shGlobalToId);
        }

        for (ShPostImpl shPost : shPostRepository.findByShFolder(shFolder)) {
            Set<ShPostAttr> shPostAttrs = shPostAttrRepository.findByShPost(shPost);
            shPostAttrRepository.deleteAllInBatch(shPostAttrs);
        }

        shPostRepository.deleteAllInBatch(shPostRepository.findByShFolder(shFolder));

        for (ShFolder shFolderChild : shFolderRepository.findByParentFolder(shFolder)) {
            shIntegrationUtilsInner.deleteFolder(shFolderChild);
        }

        shFolderRepository.delete(shFolder.getId());

        return true;
    }
}
