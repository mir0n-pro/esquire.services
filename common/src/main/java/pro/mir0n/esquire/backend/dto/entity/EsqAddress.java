/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/28/2026 mir0n  created: address DTO subentity
 * 03/03/2026 mir0n  url field added to fill()
 */

package pro.mir0n.esquire.backend.dto.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import pro.mir0n.esquire.backend.dto.EsqThing;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqAddressJpa;

import java.util.List;

@Data
@Schema(
        name = "EsqAddress",
        description = "Holds generic object information"
)

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class EsqAddress extends EsqThing {

    @Schema(
            description = "User ID", example = ""
    )
    private String id;

    @Schema(
            description = "Person kind code: 987:Primary, Secondary Contact, Joint. Part of PK", example = "987 for Primary person record"
    )
    private Integer kind;

    @Schema(
            description = "Address description or additional info", example = "Postal address of bungalo"
    )
    private String desc;


    @Schema(
            description = "Street address line 1", example = "123 1st Str"
    )
    private String addr;

    @Schema(
            description = "Street address line 2", example = "Apt 5"
    )
    private String addr2;

    @Schema(
            description = "City name", example = "Acapulco"
    )
    private String city;

    @Schema(
            description = "Company name (for biz address)", example = "mir0n&co"
    )
    private String company;

    @Schema(
            description = "Country name", example = "Mexico"
    )
    private String country;

    @Schema(
            description = "Department name (for biz address)", example = "Development"
    )
    private String department;

    @Schema(
            description = "Fax number (for biz address)", example = "+!(234)456-7890"
    )
    private String fax;

    @Schema(
            description = "Zip or postal code", example = "123456"
    )
    private String postalCode;

    @Schema(
            description = "State or province name", example = "Guerrero"
    )
    private String province;

    @Schema(
            description = "Title with company (for biz address)", example = "intern"
    )
    private String title;

    @Schema(
            description = "Website Internet URL (for biz address)", example = "www.mir0n.pro"
    )
    private String url;

    //xxx: protected is intentional! friend access
    protected  void fill(EsqEntityJpa jpa) {
        EsqAddressJpa address = (EsqAddressJpa) jpa;
        setId(jpa.getId());
        setKind(jpa.getKind());
        this.desc = address.getDesc();
        this.addr = address.getAddr();
        this.addr2 = address.getAddr2();
        this.city = address.getCity();
        this.company = address.getCompany();
        this.country = address.getCountry();
        this.department = address.getDepartment();
        this.fax = address.getFax();
        this.postalCode = address.getPostalCode();
        this.province = address.getProvince();
        this.title = address.getTitle();
        this.url = address.getUrl();
    }


}

