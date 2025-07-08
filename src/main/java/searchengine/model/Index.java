package searchengine.model;

import jakarta.persistence.*; // Используем только JPA импорты!
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "search_index")
@Getter
@Setter
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false)
    private Lemma lemma;

    @Column(name = "`rank`")
    private float rank;
}
