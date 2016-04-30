package com.artemis.link;

import com.artemis.ComponentType;
import com.artemis.Entity;
import com.artemis.World;
import com.artemis.annotations.EntityId;
import com.artemis.annotations.LinkPolicy;
import com.artemis.utils.Bag;
import com.artemis.utils.IntBag;
import com.artemis.utils.reflect.Annotation;
import com.artemis.utils.reflect.ClassReflection;
import com.artemis.utils.reflect.Field;

import static com.artemis.annotations.LinkPolicy.Policy.SKIP;
import static com.artemis.utils.reflect.ReflectionUtil.isGenericType;

class LinkFactory {
	private static final int NULL_REFERENCE = 0;
	private static final int SINGLE_REFERENCE = 1;
	private static final int MULTI_REFERENCE = 2;

	private final Bag<LinkSite> links = new Bag<LinkSite>();
	private World world;

	private final ReflexiveMutators reflexiveMutators;

	public LinkFactory(World world) {
		this.world = world;
		reflexiveMutators = new ReflexiveMutators(world);
	}

	static int getReferenceTypeId(Field f) {
		Class type = f.getType();
		if (Entity.class == type)
			return SINGLE_REFERENCE;
		if (isGenericType(f, Bag.class, Entity.class))
			return MULTI_REFERENCE;

		boolean explicitEntityId = f.getDeclaredAnnotation(EntityId.class) != null;
		if (int.class == type && explicitEntityId)
			return SINGLE_REFERENCE;
		if (IntBag.class == type && explicitEntityId)
			return MULTI_REFERENCE;

		return NULL_REFERENCE;
	}

	Bag<LinkSite> create(ComponentType ct) {
		Class<?> type = ct.getType();
		Field[] fields = ClassReflection.getDeclaredFields(type);

		links.clear();
		for (int i = 0; fields.length > i; i++) {
			Field f = fields[i];
			int referenceTypeId = getReferenceTypeId(f);
			if (referenceTypeId != NULL_REFERENCE && (SKIP != getPolicy(f))) {
				if (SINGLE_REFERENCE == referenceTypeId) {
					UniLinkSite linkSite = new UniLinkSite(world, ct, f);
					links.add(reflexiveMutators.withMutator(linkSite));
				} else if (MULTI_REFERENCE == referenceTypeId) {
					MultiLinkSite e = new MultiLinkSite(world, ct, f);
					links.add(reflexiveMutators.withMutator(e));
				}
			}
		}

		return links;
	}

	static LinkPolicy.Policy getPolicy(Field f) {
		Annotation annotation = f.getDeclaredAnnotation(LinkPolicy.class);
		if (annotation != null) {
			LinkPolicy lp = annotation.getAnnotation(LinkPolicy.class);
			return lp.value();
		}

		return null;
	}

	static class ReflexiveMutators {
		final EntityFieldMutator entityField;
		final IntFieldMutator intField;
		final IntBagFieldMutator intBagField;
		final EntityBagFieldMutator entityBagField;

		public ReflexiveMutators(World world) {
			entityField = new EntityFieldMutator(world);
			intField = new IntFieldMutator();
			intBagField = new IntBagFieldMutator(world);
			entityBagField = new EntityBagFieldMutator(world);
		}

		UniLinkSite withMutator(UniLinkSite linkSite) {
			Class type = linkSite.field.getType();
			if (Entity.class == type) {
				linkSite.fieldMutator = entityField;
			} else if (int.class == type) {
				linkSite.fieldMutator = intField;
			} else {
				throw new RuntimeException("unexpected '" + type + "', on " + linkSite.type);
			}

			return linkSite;
		}

		MultiLinkSite withMutator(MultiLinkSite linkSite) {
			Class type = linkSite.field.getType();
			if (IntBag.class == type) {
				linkSite.fieldMutator = intBagField;
			} else if (Bag.class == type) {
				linkSite.fieldMutator = entityBagField;
			} else {
				throw new RuntimeException("unexpected '" + type + "', on " + linkSite.type);
			}

			return linkSite;
		}
	}
}