/*
 * Copyright (c) 2017-2019 Globo.com
 * All rights reserved.
 *
 * This source is subject to the Apache License, Version 2.0.
 * Please see the LICENSE file for more information.
 *
 * Authors: See AUTHORS file
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.globo.pepe.api.repository;

import com.globo.pepe.common.model.munin.Connection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "connection", collectionResourceRel = "connection", itemResourceRel = "connection")
public interface ConnectionRepository extends JpaRepository<Connection, Long> {

    Page<Connection> findByNameContains(String name, Pageable pageable);

    Connection findByName(String name);

}
