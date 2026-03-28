package org.example.eventhub.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "events")

@CompoundIndexes({
        @CompoundIndex(name = "title_created_by_idx", def = "{'title': 1, 'created_by': 1}")
})
public class Event {

    @Id
    private String id;

    @Indexed(unique = true)
    private String title;

    private String description;

    private Location location;

    @Field("created_at")
    private String createdAt;

    @Indexed
    @Field("created_by")
    private String createdBy;

    @Field("started_at")
    private String startedAt;

    @Field("finished_at")
    private String finishedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String address;
    }
}