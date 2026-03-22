/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/28/2026 mir0n  created: person DTO subentity
 * 03/01/2026 mir0n  dob @Schema updated: ISO-8601 format (YYYY-MM-DD)
 */

package pro.mir0n.esquire.backend.dto.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import pro.mir0n.esquire.backend.dto.EsqThing;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqPersonJpa;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(
        name = "EsqPerson",
        description = "Holds generic object information"
)
//@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class EsqPerson extends EsqThing {

    @Schema(
            description = "First(Given) name", example = "Vasia"
    )
    private String firstName;

    @Schema(
            description = "Middle name", example = "Piter"
    )
    private String middleName;

    @Schema(
            description = "Last(Family) name", example = "Pupkin"
    )
    private String lastName;
    
    @Schema(
            description = "Title or Honorifics", example = "Dr."
    )
    private String title;
    
    @Schema(
            description = "Date of birthday, ISO-8601: YYYY-MM-DD", example = "2001/12/31"
    )
    private String dob;
    
    @Schema(
            description = "Birth place", example = "Acapulco"
    )
    private String birthPlace;

    @Schema(
            description = "Sex code: Male, Female, None", example = "M"
    )
    private String sex;

    @Schema(
            description = "Tax ID or SSN", example = "123456789"
    )
    private String taxId;

    @Schema(
            description = "Citizenship country", example = "Mexico"
    )
    private String citizenship;

    @Schema(
            description = "Martial status, Single, Married, Divorced, Separated", example = "S"
    )
    private String marStatus;

    @Schema(
            description = "Type of person ID document, Passport, Driver License, State ID, Other", example = "P"
    )
    private String personIdType;

    @Schema(
            description = "Number on person ID", example = "NG 123456"
    )
    private String personIdNumber;

    @Schema(
            description = "Email address", example = "VasiaPupkin@gmail.com"
    )
    private String email;

    @Schema(
            description = "Phone number", example = "+1 (123) 456 7890"
    )
    private String phone;

    @Schema(
            description = "Alternative Phone number", example = "+1 (123) 456 7892"
    )
    private String phone2;

/*
    public String getName() {
        return firstName +
            ((middleName != null && !middleName.isBlank()) ? " " + middleName.charAt(0) + "." : "") +
            " " + lastName;
    }
 */
    //xxx: protected is intentional! friend access
    protected  void fill(EsqEntityJpa jpa) {
        EsqPersonJpa person = (EsqPersonJpa) jpa;
        setId(jpa.getId());
        setKind(jpa.getKind());
        this.firstName = person.getFirstName();
        this.middleName = person.getMiddleName();
        this.lastName = person.getLastName();
        this.title = person.getTitle();
        this.dob = person.getDob();
        this.birthPlace = person.getBirthPlace();
        this.sex = person.getSex();
        this.taxId = person.getTaxId();
        this.citizenship = person.getCitizenship();
        this.marStatus = person.getMarStatus();
        this.personIdType = person.getPersonIdType();
        this.personIdNumber = person.getPersonIdNumber();
        this.email = person.getEmail();
        this.phone = person.getPhone();
        this.phone2 = person.getPhone2();
    }
}

