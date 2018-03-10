package com.viglet.shiohara.persistence.repository.post;

import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.shiohara.persistence.model.channel.ShChannel;
import com.viglet.shiohara.persistence.model.post.ShPost;
import com.viglet.shiohara.persistence.model.post.type.ShPostType;

public interface ShPostRepository extends JpaRepository<ShPost, UUID> {
	
	List<ShPost> findAll();

	List<ShPost> findByShChannel(ShChannel shChannel);
	
	List<ShPost> findByShChannelAndShPostType(ShChannel shChannel, ShPostType shPostType);
	
	ShPost findById(UUID id);
	
	ShPost findByTitle(String title);
	
	ShPost findByShChannelAndTitle(ShChannel shChannel, String title);
	
	ShPost save(ShPost shPost);

	@Modifying
	@Query("delete from ShPost p where p.id = ?1")
	void delete(UUID shPostId);
}
