package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "page",
        indexes = {
                @jakarta.persistence.Index(name = "idx_page_path", columnList = "path"),
                @jakarta.persistence.Index(name = "idx_page_site", columnList = "site_id")
        })
@Getter
@Setter
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false, foreignKey = @ForeignKey(name = "fk_page_site"))
    private Site site;

    @Column(name = "path", columnDefinition = "VARCHAR(512) NOT NULL")
    private String path;

    @Column(name = "code", nullable = false)
    private int code;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT NOT NULL")
    private String content;
}