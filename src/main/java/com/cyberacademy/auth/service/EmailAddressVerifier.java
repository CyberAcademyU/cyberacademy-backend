package com.cyberacademy.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Valida que un correo pueda recibir mensajes ANTES de enviar el OTP.
 *
 * Limitación importante: no es posible comprobar si un BUZÓN concreto existe
 * sin enviar (Gmail/Outlook bloquean VRFY/RCPT TO). Lo que sí se verifica aquí es:
 *   1. Formato del dominio.
 *   2. Que el dominio tenga registros MX (o A como fallback).
 *
 * Esto evita enviar códigos a dominios inexistentes o mal escritos
 * (ej: gmail.con, gmial.com), que son la causa más habitual de los rebotes.
 */
@Service
public class EmailAddressVerifier {

    private static final Logger log = LoggerFactory.getLogger(EmailAddressVerifier.class);

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?=.{1,253}$)([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$");

    private static final int MAX_CACHE_ENTRIES = 500;

    // Proveedores habituales: sirven para sugerir correcciones en dominios mal escritos
    private static final List<String> POPULAR_DOMAINS = List.of(
            "gmail.com", "googlemail.com", "hotmail.com", "outlook.com", "live.com",
            "yahoo.com", "yahoo.es", "icloud.com", "me.com", "proton.me",
            "protonmail.com", "aol.com", "gmx.com", "zoho.com");

    // Distancia máxima de edición para considerar que es un error de escritura
    private static final int MAX_TYPO_DISTANCE = 2;

    // Cache de dominios ya consultados (evita golpear DNS en cada registro)
    private final Map<String, Boolean> domainCache = new ConcurrentHashMap<>();

    public record Result(boolean valid, String message) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result fail(String message) {
            return new Result(false, message);
        }
    }

    public Result verify(String email) {
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return Result.fail("El correo electrónico no tiene un formato válido.");
        }

        String domain = email.substring(at + 1).toLowerCase();

        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            return Result.fail("El dominio del correo «" + domain + "» no es válido. Revisa que esté bien escrito.");
        }

        // Dominio parecido a un proveedor conocido: probablemente sea un typo
        String suggestion = suggestCorrection(domain);
        if (suggestion != null) {
            return Result.fail("El dominio «" + domain + "» parece un error de escritura. "
                    + "¿Quisiste decir «" + suggestion + "»?");
        }

        if (!hasMailServer(domain)) {
            return Result.fail("El dominio «" + domain + "» no existe o no puede recibir correos. "
                    + "Verifica que tu correo esté bien escrito.");
        }

        return Result.ok();
    }

    /**
     * Devuelve el dominio popular más parecido si la diferencia es un error de
     * escritura (ej: gmial.com -> gmail.com, gmail.con -> gmail.com).
     */
    private String suggestCorrection(String domain) {
        if (POPULAR_DOMAINS.contains(domain)) {
            return null;
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String popular : POPULAR_DOMAINS) {
            int distance = levenshtein(domain, popular);
            if (distance > 0 && distance <= MAX_TYPO_DISTANCE && distance < bestDistance) {
                best = popular;
                bestDistance = distance;
            }
        }
        return best;
    }

    private int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private boolean hasMailServer(String domain) {
        Boolean cached = domainCache.get(domain);
        if (cached != null) {
            return cached;
        }

        boolean exists = lookup(domain, "MX") || lookup(domain, "A");

        // Sólo se cachean los dominios válidos para evitar que un atacante
        // llene la memoria con dominios aleatorios.
        if (exists && domainCache.size() < MAX_CACHE_ENTRIES) {
            domainCache.put(domain, true);
        } else if (!exists) {
            log.debug("Dominio sin registros MX/A: [{}]", domain);
        }

        return exists;
    }

    private boolean lookup(String domain, String type) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put(Context.PROVIDER_URL, "dns:");
        // Timeouts cortos: la petición HTTP no debe quedar colgada por DNS
        env.put("com.sun.jndi.dns.timeout.initial", "3000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");

        try {
            InitialDirContext ctx = new InitialDirContext(env);
            try {
                Attributes attributes = ctx.getAttributes(domain, new String[]{type});
                Attribute attribute = attributes.get(type);
                return attribute != null && attribute.size() > 0;
            } finally {
                ctx.close();
            }
        } catch (NamingException e) {
            return false;
        }
    }
}
