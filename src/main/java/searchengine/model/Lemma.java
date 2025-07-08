// searchengine/model/Lemma.java
package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "lemma",
        indexes = {
                @jakarta.persistence.Index(name = "idx_lemma_lemma_site", columnList = "lemma,site_id", unique = true),
                @jakarta.persistence.Index(name = "idx_lemma_frequency", columnList = "frequency")
        })
@Getter
@Setter
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lemma_site"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Site site;

    @Column(name = "lemma", nullable = false, length = 255)
    private String lemma;

    @Column(name = "frequency", nullable = false)
    private int frequency;
}