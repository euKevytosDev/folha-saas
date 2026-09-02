/**
 * Isolamento multi-tenant por discriminador (establishment_id) em schema compartilhado.
 *
 * Toda entidade operacional pertence a um Establishment. Consultas e mutações
 * devem sempre filtrar pelo tenant do contexto — nunca confiar no frontend.
 */
package com.sacolao.tenant;
