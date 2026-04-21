package com.homie.finance.dto.category;

import lombok.Builder;
import lombok.Data;
import java.io.Serializable;

@Data
@Builder
public class CategoryResponse implements Serializable {
    private String id;
    private String name;
    private String type;
    private String icon;
}