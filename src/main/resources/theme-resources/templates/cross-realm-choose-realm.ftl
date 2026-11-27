<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${msg("crossRealmChooseTitle")}
    <#elseif section = "form">
        <p>${msg("crossRealmChooseInstruction")}</p>
        <form id="cross-realm-choices" action="${url.loginAction}" method="post">
            <#list choices as choice>
                <div class="${properties.kcFormGroupClass!}">
                    <button type="submit" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}" name="${choiceField}" value="${choice.index}">
                        <span class="choice-label">${choice.label}</span>
                        <#if choice.realmName?has_content>
                            <span class="choice-realm">${choice.realmName}</span>
                        </#if>
                    </button>
                </div>
            </#list>
        </form>
    </#if>
</@layout.registrationLayout>
