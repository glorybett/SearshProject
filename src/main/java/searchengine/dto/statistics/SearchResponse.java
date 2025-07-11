package searchengine.dto.statistics;

import lombok.Data;

import java.util.List;

@Data
public class SearchResponse {
    private boolean result;
    private int count;
    private List<SearchData> data;
    private String error;
}
