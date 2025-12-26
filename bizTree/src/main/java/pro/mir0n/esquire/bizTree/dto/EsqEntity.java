/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.bizTree.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import pro.mir0n.esquire.bizTree.jpa.EsqEntityJpa;
import pro.mir0n.esquire.bizTree.jpa.EsqNameValueJpa;
import pro.mir0n.esquire.bizTree.jpa.EsqTreeNodeJpa;

import java.util.List;

@Data
@Schema(
        name = "Entity",
        description = "Schema to holdGeneric Entity information"
)

public abstract class EsqEntity {
    abstract public String getId();
    abstract public void setId(String id);
    abstract public Integer getKind();
    abstract public void setKind(Integer kind);
    abstract public String getName();
    abstract public void setName(String name);
    abstract public String getDesc();
    abstract public void setDesc(String desc);

    protected abstract void fillDetails(EsqEntityJpa jpa);
    protected abstract void fillCustom(List<EsqNameValueJpa> custom);
    protected abstract void fillChildren(List<EsqTreeNodeJpa> children);

    public void fill (EsqEntityJpa jpa, List<EsqNameValueJpa> custom, List<EsqTreeNodeJpa> children) {
        setId(jpa.getId());
        setKind(jpa.getKind());
        setName (jpa.getName());
        setDesc(jpa.getDesc());
        fillDetails(jpa);
        if (custom != null) {
            fillCustom(custom);
        }
        if (custom != null) {
            fillChildren(children);
        }
    }

}

